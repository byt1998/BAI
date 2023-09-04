package bai.bcp.yolov8ncnn;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.media.ExifInterface;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraX;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageAnalysisConfig;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.PreviewConfig;
import androidx.camera.core.UseCase;
import androidx.core.app.ActivityCompat;
import androidx.core.widget.ContentLoadingProgressBar;
import androidx.lifecycle.LifecycleOwner;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import wseemann.media.FFmpegMediaMetadataRetriever;

public class MainActivity extends AppCompatActivity {
    public static int YoloV8 = 1;
    public static int USE_MODEL = YoloV8;
    private static final int REQUEST_CAMERA = 100;
    private static final int PICK_IMAGE_REQUEST = 101;

    private static final int STORAGE_PERMISSION_REQUEST_CODE = 102;
    private static String[] PERMISSIONS_CAMERA = {
            Manifest.permission.CAMERA
    };
    private final String BASE_FOLDER = "BCP_COUNTER";
    private static final String TAG = "AndroidCameraApi";
    private final String FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS";
    public static boolean USE_GPU = false;
    public static CameraX.LensFacing CAMERA_ID = CameraX.LensFacing.BACK;

    private Yolov8Ncnn yolov8ncnn = new Yolov8Ncnn();
    private AtomicBoolean detectCamera = new AtomicBoolean(false);
    private AtomicBoolean detectPhoto = new AtomicBoolean(false);
    private long startTime = 0;
    private long endTime = 0;
    private int width;
    private int height;
    double total_fps = 0;
    int fps_count = 0;
    protected Bitmap mutableBitmap;
    ExecutorService detectService = Executors.newSingleThreadExecutor();
    FFmpegMediaMetadataRetriever mmr;

    private TextureView cameraView;

    private Button buttonSwitchCamera, upimgButton, imageBackButton, cameraButton, saveimgButton;
    private ImageView selectedImage;

    private ImageCapture imageCapture;

    private ContentLoadingProgressBar progressCircular;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }
    private boolean isShowingCamera = true;
    private boolean isImageDisplayed =false;
    protected CameraDevice cameraDevice;
    protected CameraCaptureSession cameraCaptureSession;
    protected CaptureRequest.Builder captureRequestBuilder;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;
    private Size imageDimension;
    private ImageReader imageReader;
    private File file;
    private File folder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        int permission = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        if (permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    PERMISSIONS_CAMERA,
                    REQUEST_CAMERA
            );
            finish();
        }

        initModel();
        initViewID();

        TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
                startCamera();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
                updateTransform();
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture texture) {
                Bitmap bitmap = cameraView.getBitmap();

                Box[] result = null;
                if (result != null) {
                    result = yolov8ncnn.detect(bitmap);
                    mutableBitmap = drawBoxRects(bitmap, result);
                }
                selectedImage.setImageBitmap(mutableBitmap);
            }
        };
//
//        selectedImage.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                if (detectCamera.get()) {
//                    detectCamera.set(false);
//                    startCamera();
//                }
//            }
//        });
//
//
//        cameraView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
//            @Override
//            public void onLayoutChange(View view, int i, int i1, int i2, int i3, int i4, int i5, int i6, int i7) {
//                updateTransform();
//            }
//        });
//
//        cameraView.post(new Runnable() {
//            @Override
//            public void run() {
//                startCamera();
//            }
//        });

    }

    protected void initViewID() {
        cameraView = findViewById(R.id.cameraview);
//        if(cameraView != null)
//            cameraView.setSurfaceTexture((SurfaceTexture) textureListener);
//        previewView = findViewById(R.id.previewView);
        buttonSwitchCamera = findViewById(R.id.buttonSwitchCamera);
        selectedImage = findViewById(R.id.selectedImage);
        upimgButton = findViewById(R.id.upimgButton);
        cameraButton = findViewById(R.id.cameraButton);
        imageBackButton = findViewById(R.id.imageBackButton);
        progressCircular = findViewById(R.id.progressCircular);
        saveimgButton = findViewById(R.id.saveimgButton);

        progressCircular.setVisibility(View.GONE);
    }

    protected void initModel() {
        if (USE_MODEL == YoloV8){
            yolov8ncnn.loadModel(getAssets(), USE_GPU);
            Log.d("MainActivity", "YOLOv8 model loaded successfully");
        } else {
            Log.e("MainActivity", "YOLOv8ncnn loadModel failed");
        }
    }

    private void updateTransform() {
        Matrix matrix = new Matrix();
        // Compute the center of the view finder
        float centerX = cameraView.getWidth() / 2f;
        float centerY = cameraView.getHeight() / 2f;

        float[] rotations = {0, 90, 180, 270};
        // Correct preview output to account for display rotation
        float rotationDegrees = rotations[cameraView.getDisplay().getRotation()];

        matrix.postRotate(-rotationDegrees, centerX, centerY);

        // Finally, apply transformations to our TextureView
        cameraView.setTransform(matrix);
    }

    private void startCamera() {
        CameraX.unbindAll();
        // 1. preview
        PreviewConfig previewConfig = new PreviewConfig.Builder()
                .setLensFacing(CAMERA_ID)
//                .setTargetAspectRatio(Rational.NEGATIVE_INFINITY)  // 宽高比
                .setTargetResolution(new Size(480, 640))  // 分辨率
                .build();

        Preview preview = new Preview(previewConfig);
        preview.setOnPreviewOutputUpdateListener(new Preview.OnPreviewOutputUpdateListener() {
            @Override
            public void onUpdated(Preview.PreviewOutput output) {
                ViewGroup parent = (ViewGroup) cameraView.getParent();
                parent.removeView(cameraView);
                parent.addView(cameraView, 0);

                cameraView.setSurfaceTexture(output.getSurfaceTexture());
                updateTransform();
            }
        });
        DetectAnalyzer detectAnalyzer = new DetectAnalyzer();
        CameraX.bindToLifecycle((LifecycleOwner) this, preview, gainAnalyzer(detectAnalyzer));

    }


    private UseCase gainAnalyzer(DetectAnalyzer detectAnalyzer) {
        ImageAnalysisConfig.Builder analysisConfigBuilder = new ImageAnalysisConfig.Builder();
        analysisConfigBuilder.setImageReaderMode(ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE);
        analysisConfigBuilder.setTargetResolution(new Size(480, 640));  // 输出预览图像尺寸
        ImageAnalysisConfig config = analysisConfigBuilder.build();
        ImageAnalysis analysis = new ImageAnalysis(config);
        analysis.setAnalyzer(detectAnalyzer);
        return analysis;
    }

    private Bitmap imageToBitmap(ImageProxy image) {
        byte[] nv21 = imagetToNV21(image);

        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 100, out);
        byte[] imageBytes = out.toByteArray();
        try {
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }

    private byte[] imagetToNV21(ImageProxy image) {
        ImageProxy.PlaneProxy[] planes = image.getPlanes();
        ImageProxy.PlaneProxy y = planes[0];
        ImageProxy.PlaneProxy u = planes[1];
        ImageProxy.PlaneProxy v = planes[2];
        ByteBuffer yBuffer = y.getBuffer();
        ByteBuffer uBuffer = u.getBuffer();
        ByteBuffer vBuffer = v.getBuffer();
        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();
        byte[] nv21 = new byte[ySize + uSize + vSize];
        // U and V are swapped
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        return nv21;
    }

    private class DetectAnalyzer implements ImageAnalysis.Analyzer {

        @Override
        public void analyze(ImageProxy image, final int rotationDegrees) {
            detectOnModel(image, rotationDegrees);
        }
    }


    private void detectOnModel(ImageProxy image, final int rotationDegrees) {
        if (detectCamera.get() || detectPhoto.get()) {
            return;
        }
        detectCamera.set(true);
        startTime = System.currentTimeMillis();
        final Bitmap bitmapsrc = imageToBitmap(image);  // 格式转换
        if (detectService == null) {
            detectCamera.set(false);
            return;
        }
        detectService.execute(new Runnable() {
            @Override
            public void run() {
                Matrix matrix = new Matrix();
                matrix.postRotate(rotationDegrees);
                width = bitmapsrc.getWidth();
                height = bitmapsrc.getHeight();
                Bitmap bitmap = Bitmap.createBitmap(bitmapsrc, 0, 0, width, height, matrix, false);

                detectAndDraw(bitmap);
                showResultOnUI();
            }
        });
    }

    protected void showResultOnUI() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                detectCamera.set(false);
                selectedImage.setImageBitmap(mutableBitmap);
                endTime = System.currentTimeMillis();
                long dur = endTime - startTime;
                float fps = (float) (1000.0 / dur);
                total_fps = (total_fps == 0) ? fps : (total_fps + fps);
                fps_count++;
            }
        });
    }

    protected Bitmap drawBoxRects(Bitmap mutableBitmap, Box[] results) {
        if (results == null || results.length <= 0) {
            return mutableBitmap;
        }
        Canvas canvas = new Canvas(mutableBitmap);
        final Paint boxPaint = new Paint();
        boxPaint.setAlpha(200);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(4 * mutableBitmap.getWidth() / 800.0f);
        boxPaint.setTextSize(30 * mutableBitmap.getWidth() / 800.0f);
        for (Box box : results) {
            boxPaint.setColor(box.getColor());
            boxPaint.setStyle(Paint.Style.FILL);
            canvas.drawText(box.getLabel() + String.format(Locale.US, " %.3f", box.getProb()), box.x0 + 3, box.y0 + 30 * mutableBitmap.getWidth() / 1000.0f, boxPaint);
            boxPaint.setStyle(Paint.Style.STROKE);
            canvas.drawRect(box.getRect(), boxPaint);
        }
        return mutableBitmap;
    }

    protected Bitmap detectAndDraw(Bitmap image) {
        Box[] result = null;
        if (result != null) {
            result = yolov8ncnn.detect(image);
            mutableBitmap = drawBoxRects(image, result);
            return mutableBitmap;
        } else
            detectCamera.set(false);
        return image;
    }

    @Override
    protected void onDestroy() {
        detectCamera.set(false);
        if (detectService != null) {
            detectService.shutdown();
            detectService = null;
        }
        if (mmr != null) {
            mmr.release();
        }
        CameraX.unbindAll();
        super.onDestroy();
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Izin kamera diberikan, lakukan tindakan yang diperlukan
                startCamera();
            } else {
                // Izin kamera ditolak, Anda bisa memberikan pesan kepada pengguna atau mengambil tindakan lain
                Toast.makeText(this, "Izin kamera ditolak", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (data == null) {
            return;
        }
        if (requestCode == PICK_IMAGE_REQUEST) {
            // photo
            runByPhoto(requestCode, resultCode, data);
        } else {
            Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show();
        }
    }

    public void runByPhoto(int requestCode, int resultCode, @Nullable Intent data) {
        if (resultCode != RESULT_OK || data == null) {
            Toast.makeText(this, "Photo error", Toast.LENGTH_SHORT).show();
            return;
        }
        detectPhoto.set(true);
        final Bitmap image = getPicture(data.getData());
        if (image == null) {
            Toast.makeText(this, "Photo is null", Toast.LENGTH_SHORT).show();
            return;
        }
        CameraX.unbindAll();
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                long start = System.currentTimeMillis();
                mutableBitmap = image.copy(Bitmap.Config.ARGB_8888, true);
                width = image.getWidth();
                height = image.getHeight();

                mutableBitmap = detectAndDraw(mutableBitmap);

                final long dur = System.currentTimeMillis() - start;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        selectedImage.setImageBitmap(mutableBitmap);
                    }
                });
            }
        }, "photo detect");
        thread.start();
    }

    public void runByVideo(int requestCode, int resultCode, @Nullable Intent data) {
        if (resultCode != RESULT_OK || data == null) {
            Toast.makeText(this, "Video error", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Uri uri = data.getData();
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null) {
                cursor.moveToFirst();
                // String imgNo = cursor.getString(0); // 编号
                String v_path = cursor.getString(1); // 文件路径
                String v_size = cursor.getString(2); // 大小
                String v_name = cursor.getString(3); // 文件名
            } else {
                Toast.makeText(this, "Video is null", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Video is null", Toast.LENGTH_SHORT).show();
        }
    }

    public Bitmap getPicture(Uri selectedImage) {
        String[] filePathColumn = {MediaStore.Images.Media.DATA};
        Cursor cursor = this.getContentResolver().query(selectedImage, filePathColumn, null, null, null);
        if (cursor == null) {
            return null;
        }
        cursor.moveToFirst();
        int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
        String picturePath = cursor.getString(columnIndex);
        cursor.close();
        Bitmap bitmap = BitmapFactory.decodeFile(picturePath);
        if (bitmap == null) {
            return null;
        }
        int rotate = readPictureDegree(picturePath);
        return rotateBitmapByDegree(bitmap, rotate);
    }

    public int readPictureDegree(String path) {
        int degree = 0;
        try {
            ExifInterface exifInterface = new ExifInterface(path);
            int orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    degree = 90;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    degree = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    degree = 270;
                    break;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return degree;
    }

    public Bitmap rotateBitmapByDegree(Bitmap bm, int degree) {
        Bitmap returnBm = null;
        Matrix matrix = new Matrix();
        matrix.postRotate(degree);
        try {
            returnBm = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(),
                    bm.getHeight(), matrix, true);
        } catch (OutOfMemoryError e) {
            e.printStackTrace();
        }
        if (returnBm == null) {
            returnBm = bm;
        }
        if (bm != returnBm) {
            bm.recycle();
        }
        return returnBm;
    }

    /**
     * letterbox (slow)
     *
     * @param srcBitmap
     * @param srcWidth
     * @param srcHeight
     * @param dstWidth
     * @param dstHeight
     * @param matrix
     * @return
     */
    public static Bitmap letterbox(Bitmap srcBitmap, int srcWidth, int srcHeight, int dstWidth, int dstHeight, Matrix matrix) {
        long timeStart = System.currentTimeMillis();
        float scale = Math.min((float) dstWidth / srcWidth, (float) dstHeight / srcHeight);
        int nw = (int) (srcWidth * scale);
        int nh = (int) (srcHeight * scale);
        matrix.postScale((float) nw / srcWidth, (float) nh / srcHeight);
        Bitmap bitmap = Bitmap.createBitmap(srcBitmap, 0, 0, srcWidth, srcHeight, matrix, false);
        Bitmap newBitmap = Bitmap.createBitmap(dstWidth, dstHeight, Bitmap.Config.ARGB_8888);//创建和目标相同大小的空Bitmap
        Canvas canvas = new Canvas(newBitmap);
        Paint paint = new Paint();
        // 针对绘制bitmap添加抗锯齿
        PaintFlagsDrawFilter pfd = new PaintFlagsDrawFilter(0, Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        paint.setFilterBitmap(false);  // 对Bitmap进行滤波处理
        paint.setAntiAlias(true);  // 设置抗锯齿
        canvas.setDrawFilter(pfd);
        canvas.drawBitmap(bitmap, null,
                new Rect((dstHeight - nh) / 2, (dstWidth - nw) / 2,
                        (dstHeight - nh) / 2 + nh, (dstWidth - nw) / 2 + nw),
                paint);
        long timeDur = System.currentTimeMillis() - timeStart;
//        Log.d(TAG, "letterbox time:" + timeDur);
        return newBitmap;
    }

}
