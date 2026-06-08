package io.flutter.plugins.flutterexifrotation;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.provider.MediaStore;
import androidx.annotation.NonNull;
import androidx.exifinterface.media.ExifInterface;
import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * FlutterExifRotationPlugin
 */
public class FlutterExifRotationPlugin implements FlutterPlugin, MethodCallHandler {
    private static final String CHANNEL_NAME = "flutter_exif_rotation";
    private static final ExecutorService threadPool = Executors.newSingleThreadExecutor();

    private Context applicationContext;
    private MethodChannel methodChannel;

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        applicationContext = binding.getApplicationContext();
        methodChannel = new MethodChannel(binding.getBinaryMessenger(), CHANNEL_NAME);
        methodChannel.setMethodCallHandler(this);
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        applicationContext = null;
        if (methodChannel != null) {
            methodChannel.setMethodCallHandler(null);
            methodChannel = null;
        }
    }

    @Override
    public void onMethodCall(@NonNull final MethodCall call, @NonNull final MethodChannel.Result result) {
        threadPool.execute(new Runnable() {
            @Override
            public void run() {
                if (call.method.equals("rotateImage")) {
                    launchRotateImage(call, result);
                } else {
                    result.notImplemented();
                }
            }
        });
    }

    private void launchRotateImage(MethodCall call, MethodChannel.Result result) {
        String photoPath = call.argument("path");
        Boolean save = argument(call, "save", false);
        if (save == null) {
            save = false;
        }
        int orientation;
        try {
            if (photoPath == null) {
                result.error("error", "Path is null", null);
                return;
            }
            ExifInterface ei = new ExifInterface(photoPath);

            orientation = ei.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_UNDEFINED
            );
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap bitmap = BitmapFactory.decodeFile(photoPath, options);
            if (bitmap == null) {
                result.error("error", "Bitmap decoding failed", null);
                return;
            }
            Bitmap rotatedBitmap;
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    rotatedBitmap = rotate(bitmap, 90f);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    rotatedBitmap = rotate(bitmap, 180f);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    rotatedBitmap = rotate(bitmap, 270f);
                    break;
                case ExifInterface.ORIENTATION_NORMAL:
                default:
                    rotatedBitmap = bitmap;
                    break;
            }
            File file = new File(photoPath);
            FileOutputStream fOut = new FileOutputStream(file);
            rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, fOut);
            fOut.flush();
            fOut.close();
            if (save) {
                MediaStore.Images.Media.insertImage(
                    applicationContext.getContentResolver(),
                    file.getAbsolutePath(),
                    file.getName(),
                    file.getName()
                );
            }
            result.success(file.getPath());
        } catch (IOException e) {
            result.error("error", "IOexception", null);
            e.printStackTrace();
        }
    }

    private static Bitmap rotate(Bitmap source, float angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    private static <T> T argument(MethodCall call, String key, T defaultValue) {
        if (!call.hasArgument(key)) {
            return defaultValue;
        }
        T val = call.argument(key);
        return val != null ? val : defaultValue;
    }
}
