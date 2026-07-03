package com.privatetransform.guandanassistant.capture;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import com.privatetransform.guandanassistant.AssistantStore;
import com.privatetransform.guandanassistant.R;

import java.nio.ByteBuffer;

public final class ScreenCaptureService extends Service {
    private static final String CHANNEL_ID = "screen_capture";
    private static final String ACTION_STOP = "com.privatetransform.guandanassistant.STOP_CAPTURE";
    private static final String EXTRA_RESULT_CODE = "result_code";
    private static final String EXTRA_DATA = "data";
    private static final String EXTRA_CONTINUOUS = "continuous";
    private static final long FRAME_INTERVAL_MS = 250L;

    private MediaProjection projection;
    private ImageReader imageReader;
    private VirtualDisplay virtualDisplay;
    private boolean continuous;
    private boolean processedSingleFrame;
    private long lastProcessMs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private CardRecognizer recognizer;

    public static void start(Context context, int resultCode, Intent data, boolean continuous) {
        Intent intent = new Intent(context, ScreenCaptureService.class);
        intent.putExtra(EXTRA_RESULT_CODE, resultCode);
        intent.putExtra(EXTRA_DATA, data);
        intent.putExtra(EXTRA_CONTINUOUS, continuous);
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
        else context.startService(intent);
    }

    public static void stop(Context context) {
        Intent intent = new Intent(context, ScreenCaptureService.class);
        intent.setAction(ACTION_STOP);
        context.startService(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            finish("实时视觉识别已停止");
            return START_NOT_STICKY;
        }
        if (recognizer == null) recognizer = new CardRecognizer(this);
        startForeground(31, notification("正在实时视觉识别牌面"));
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        continuous = intent.getBooleanExtra(EXTRA_CONTINUOUS, false);
        int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
        Intent data = intent.getParcelableExtra(EXTRA_DATA);
        MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        if (manager == null || data == null) {
            finish("录屏权限数据无效");
            return START_NOT_STICKY;
        }
        projection = manager.getMediaProjection(resultCode, data);
        if (projection == null) {
            finish("无法创建录屏会话");
            return START_NOT_STICKY;
        }
        projection.registerCallback(new MediaProjection.Callback() {
            @Override public void onStop() { cleanup(); }
        }, handler);
        AssistantStore.setWatching(continuous);
        sendState();
        startCapture();
        return continuous ? START_STICKY : START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        cleanup();
        AssistantStore.setWatching(false);
        sendState();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startCapture() {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager == null) {
            finish("无法读取屏幕尺寸");
            return;
        }
        windowManager.getDefaultDisplay().getRealMetrics(metrics);
        imageReader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 2);
        imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                handleImage(reader);
            }
        }, handler);
        virtualDisplay = projection.createVirtualDisplay(
                "guan-dan-capture",
                metrics.widthPixels,
                metrics.heightPixels,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(),
                null,
                handler);
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                if (!continuous && !processedSingleFrame) finish("取帧超时，请重新授权录屏");
            }
        }, 3000);
    }

    private void handleImage(ImageReader reader) {
        Image image = reader.acquireLatestImage();
        if (image == null) return;
        try {
            long now = System.currentTimeMillis();
            if (continuous && now - lastProcessMs < FRAME_INTERVAL_MS) return;
            lastProcessMs = now;
            processedSingleFrame = true;
            Bitmap bitmap = imageToBitmap(image);
            CardRecognizer.RecognitionResult result = recognizer.recognize(bitmap);
            AssistantStore.applyRecognition(result);
            sendScan();
            if (!continuous) stopSelf();
        } finally {
            image.close();
        }
    }

    private Bitmap imageToBitmap(Image image) {
        Image.Plane plane = image.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        int pixelStride = plane.getPixelStride();
        int rowStride = plane.getRowStride();
        int rowPadding = rowStride - pixelStride * image.getWidth();
        Bitmap padded = Bitmap.createBitmap(image.getWidth() + rowPadding / pixelStride, image.getHeight(), Bitmap.Config.ARGB_8888);
        padded.copyPixelsFromBuffer(buffer);
        return Bitmap.createBitmap(padded, 0, 0, image.getWidth(), image.getHeight());
    }

    private void finish(String message) {
        AssistantStore.updateScan(message, null);
        sendScan();
        stopSelf();
    }

    private void cleanup() {
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        if (projection != null) {
            projection.stop();
            projection = null;
        }
    }

    private void sendScan() {
        sendBroadcast(new Intent(AssistantStore.ACTION_SCAN_RESULT).setPackage(getPackageName()));
    }

    private void sendState() {
        sendBroadcast(new Intent(AssistantStore.ACTION_STATE_CHANGED).setPackage(getPackageName()));
    }

    private Notification notification(String text) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && manager != null) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "掼蛋实时识牌", NotificationManager.IMPORTANCE_LOW);
            manager.createNotificationChannel(channel);
        }
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("掼蛋辅助")
                .setContentText(text)
                .build();
    }
}
