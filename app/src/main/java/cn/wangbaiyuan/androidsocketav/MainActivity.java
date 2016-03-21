package cn.wangbaiyuan.androidsocketav;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.SocketException;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback, Camera.PreviewCallback, handleReceiveData {

    private Camera camera;
    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    UDPServer up;
    UDPServer upa;
    private ImageView imageView;
    private float mCameraOrientation;

    public MainActivity() {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        surfaceView = (SurfaceView) findViewById(R.id.surfaceView);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        surfaceHolder.addCallback(this);
//        final Button button=(Button)findViewById(R.id.button);
//        button.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//
//            }
//        });
        imageView = (ImageView) findViewById(R.id.imageView);
        Thread service = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    up = new UDPServer(8804);
                    up.setReceiveCallback(MainActivity.this);
                    up.start();

                    upa = new UDPServer(8805);
                    up.setReceiveCallback(new handleReceiveData() {
                        @Override
                        public void handleReceive(byte[] data) {

                        }
                    });

                    // button.setEnabled(false);
                } catch (SocketException e) {
                    //button.setEnabled(true);
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        });
        service.start();

    }

    private void initCanmera() {
        int cameras = Camera.getNumberOfCameras();
        Camera.CameraInfo info = new Camera.CameraInfo();
        for (int i = 0; i < cameras; i++) {
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                camera = Camera.open(i);
                break;
            }
        }
        //没有前置摄像头
        if (camera == null) camera = Camera.open();
        try {
            camera.setPreviewDisplay(surfaceHolder);
            camera.setPreviewCallback(this);
        } catch (Exception e) {
            camera.release();//释放资源
            camera = null;
        }
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {

        Camera.Size previewSize = camera.getParameters().getPreviewSize();

        int[] rgb = decodeYUV420SP(data, previewSize.width, previewSize.height);
        Bitmap bmp = Bitmap.createBitmap(rgb, previewSize.width, previewSize.height, Bitmap.Config.ARGB_8888);
        int smallWidth, smallHeight;
        int dimension = 200;

        if (previewSize.width > previewSize.height) {
            smallWidth = dimension;
            smallHeight = dimension * previewSize.height / previewSize.width;
        } else {
            smallHeight = dimension;
            smallWidth = dimension * previewSize.width / previewSize.height;
        }

        Matrix matrix = new Matrix();
        matrix.postRotate(mCameraOrientation);

        Bitmap bmpSmall = Bitmap.createScaledBitmap(bmp, smallWidth, smallHeight, false);
        Bitmap bmpSmallRotated = Bitmap.createBitmap(bmpSmall, 0, 0, smallWidth, smallHeight, matrix, false);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bmpSmallRotated.compress(Bitmap.CompressFormat.WEBP, 80, baos);

        up.sendMsg(baos.toByteArray());


    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        initCanmera();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        int currentCamera = Camera.CameraInfo.CAMERA_FACING_FRONT;
        Camera.Parameters parameters = camera.getParameters();//得到相机设置参数
        Camera.Size size = camera.getParameters().getPreviewSize(); //获取预览大小

        parameters.setPictureFormat(PixelFormat.JPEG);//设置图片格式
        Camera.CameraInfo info = new Camera.CameraInfo();
        camera.getCameraInfo(currentCamera, info);
        int rotation = this.getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }
        int resultA = 0, resultB = 0;
        if (currentCamera == Camera.CameraInfo.CAMERA_FACING_BACK) {
            resultA = (info.orientation - degrees + 360) % 360;
            resultB = (info.orientation - degrees + 360) % 360;
            camera.setDisplayOrientation(resultA);
        } else {
            resultA = (360 + 360 - info.orientation - degrees) % 360;
            resultB = (info.orientation + degrees) % 360;
            camera.setDisplayOrientation(resultA);
        }
        camera.setPreviewCallback(this);
        parameters.setRotation(resultB);
        mCameraOrientation = resultB;
        camera.setParameters(parameters);
        camera.startPreview();//开始预览
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (camera != null) {
            camera.setPreviewCallback(null);
            camera.stopPreview();
            camera.release();
            camera = null;
        }
    }

    protected void setDisplayOrientation(Camera camera, int angle) {
        try {
            Method downPolymorphic = camera.getClass().getMethod("setDisplayOrientation", new Class[]{int.class});
            if (downPolymorphic != null)
                downPolymorphic.invoke(camera, new Object[]{angle});
        } catch (Exception e1) {
            e1.printStackTrace();
        }
    }

    @Override
    public void handleReceive(final byte[] data) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Bitmap bit = BitmapFactory.decodeByteArray(data, 0, data.length);
                imageView.setImageBitmap(bit);
            }
        });

    }

    public int[] decodeYUV420SP(byte[] yuv420sp, int width, int height) {
        final int frameSize = width * height;
        int rgb[] = new int[width * height];
        for (int j = 0, yp = 0; j < height; j++) {
            int uvp = frameSize + (j >> 1) * width, u = 0, v = 0;
            for (int i = 0; i < width; i++, yp++) {
                int y = (0xff & ((int) yuv420sp[yp])) - 16;
                if (y < 0) y = 0;
                if ((i & 1) == 0) {
                    v = (0xff & yuv420sp[uvp++]) - 128;
                    u = (0xff & yuv420sp[uvp++]) - 128;
                }
                int y1192 = 1192 * y;
                int r = (y1192 + 1634 * v);
                int g = (y1192 - 833 * v - 400 * u);
                int b = (y1192 + 2066 * u);
                if (r < 0) r = 0;
                else if (r > 262143) r = 262143;
                if (g < 0) g = 0;
                else if (g > 262143) g = 262143;
                if (b < 0) b = 0;
                else if (b > 262143) b = 262143;
                rgb[yp] = 0xff000000 | ((r << 6) & 0xff0000)
                        | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
            }
        }
        return rgb;
    }
}
