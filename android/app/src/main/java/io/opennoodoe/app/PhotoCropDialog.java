package io.opennoodoe.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.FrameLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class PhotoCropDialog {
    public interface Callback {
        void onCropped(Uri croppedImage);
    }

    private PhotoCropDialog() {}

    public static void show(Activity activity, Uri source, int slot, Callback callback) {
        show(activity, source, "gallery-drafts", "slot-" + slot + ".jpg", callback);
    }

    public static void showTheme(Activity activity, Uri source, int location, Callback callback) {
        String name = location == NoodoeService.LOCATION_CLOCK
                ? "clock-background.jpg" : "speed-background.jpg";
        show(activity, source, "theme-drafts", name, callback);
    }

    private static void show(Activity activity, Uri source, String directoryName,
            String fileName, Callback callback) {
        final Bitmap bitmap;
        try {
            bitmap = decode(activity, source);
        } catch (IOException error) {
            new AlertDialog.Builder(activity)
                    .setMessage(activity.getString(R.string.photo_crop_load_failed))
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }

        CropView cropView = new CropView(activity, bitmap);
        FrameLayout container = new FrameLayout(activity);
        int padding = dp(activity, 12);
        container.setPadding(padding, 0, padding, 0);
        container.addView(cropView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.photo_crop_title)
                .setView(container)
                .setNegativeButton(R.string.action_cancel, null)
                .setNeutralButton(R.string.photo_crop_reset, null)
                .setPositiveButton(R.string.photo_crop_use, null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                    .setOnClickListener(view -> cropView.reset());
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                try {
                    Uri result = save(activity, cropView.render(480), directoryName, fileName);
                    callback.onCropped(result);
                    dialog.dismiss();
                } catch (IOException error) {
                    new AlertDialog.Builder(activity)
                            .setMessage(activity.getString(R.string.photo_crop_save_failed))
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                }
            });
        });
        dialog.setOnDismissListener(ignored -> bitmap.recycle());
        dialog.show();
    }

    private static Bitmap decode(Context context, Uri source) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = context.getContentResolver().openInputStream(source)) {
            BitmapFactory.decodeStream(input, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("invalid image bounds");
        }
        int sample = 1;
        while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap decoded;
        try (InputStream input = context.getContentResolver().openInputStream(source)) {
            decoded = BitmapFactory.decodeStream(input, null, options);
        }
        if (decoded == null) throw new IOException("image decode failed");

        int rotation = readRotation(context, source);
        if (rotation == 0) return decoded;
        Matrix matrix = new Matrix();
        matrix.setRotate(rotation);
        Bitmap rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.getWidth(),
                decoded.getHeight(), matrix, true);
        if (rotated != decoded) decoded.recycle();
        return rotated;
    }

    private static int readRotation(Context context, Uri source) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return 0;
        try (InputStream input = context.getContentResolver().openInputStream(source)) {
            if (input == null) return 0;
            int orientation = new ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            if (orientation == ExifInterface.ORIENTATION_ROTATE_90) return 90;
            if (orientation == ExifInterface.ORIENTATION_ROTATE_180) return 180;
            if (orientation == ExifInterface.ORIENTATION_ROTATE_270) return 270;
        } catch (IOException ignored) {
        }
        return 0;
    }

    private static Uri save(Context context, Bitmap bitmap, String directoryName,
            String fileName) throws IOException {
        File directory = new File(context.getFilesDir(), directoryName);
        if (!directory.isDirectory() && !directory.mkdirs()) {
            bitmap.recycle();
            throw new IOException("cannot create gallery draft directory");
        }
        File target = new File(directory, fileName);
        try (FileOutputStream output = new FileOutputStream(target)) {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)) {
                throw new IOException("JPEG compression failed");
            }
            output.flush();
            output.getFD().sync();
        } finally {
            bitmap.recycle();
        }
        return Uri.fromFile(target);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static final class CropView extends View {
        private static final float MAX_USER_SCALE = 8.0f;

        private final Bitmap bitmap;
        private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint shadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint guidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix imageMatrix = new Matrix();
        private final RectF crop = new RectF();
        private final ScaleGestureDetector scaleDetector;
        private float baseScale;
        private float userScale = 1.0f;
        private float translateX;
        private float translateY;
        private float lastX;
        private float lastY;
        private boolean dragging;

        CropView(Context context, Bitmap bitmap) {
            super(context);
            this.bitmap = bitmap;
            setBackgroundColor(Color.BLACK);
            shadePaint.setColor(Color.argb(178, 0, 0, 0));
            shadePaint.setStyle(Paint.Style.FILL);
            borderPaint.setColor(Color.WHITE);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(dp(context, 2));
            guidePaint.setColor(Color.argb(105, 255, 255, 255));
            guidePaint.setStyle(Paint.Style.STROKE);
            guidePaint.setStrokeWidth(dp(context, 1));
            scaleDetector = new ScaleGestureDetector(context,
                    new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                        @Override
                        public boolean onScale(ScaleGestureDetector detector) {
                            scaleBy(detector.getScaleFactor(), detector.getFocusX(),
                                    detector.getFocusY());
                            return true;
                        }
                    });
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int maximumHeight = MeasureSpec.getSize(heightMeasureSpec);
            int size = maximumHeight > 0 ? Math.min(width, maximumHeight) : width;
            setMeasuredDimension(size, size);
        }

        @Override
        protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
            float inset = dp(getContext(), 12);
            float size = Math.min(width, height) - inset * 2.0f;
            float left = (width - size) / 2.0f;
            float top = (height - size) / 2.0f;
            crop.set(left, top, left + size, top + size);
            reset();
        }

        void reset() {
            if (crop.isEmpty()) return;
            baseScale = Math.max(crop.width() / bitmap.getWidth(),
                    crop.height() / bitmap.getHeight());
            userScale = 1.0f;
            translateX = crop.centerX() - bitmap.getWidth() * baseScale / 2.0f;
            translateY = crop.centerY() - bitmap.getHeight() * baseScale / 2.0f;
            updateMatrix();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawBitmap(bitmap, imageMatrix, bitmapPaint);

            Path shade = new Path();
            shade.setFillType(Path.FillType.EVEN_ODD);
            shade.addRect(0, 0, getWidth(), getHeight(), Path.Direction.CW);
            shade.addCircle(crop.centerX(), crop.centerY(), crop.width() / 2.0f,
                    Path.Direction.CW);
            canvas.drawPath(shade, shadePaint);
            canvas.drawCircle(crop.centerX(), crop.centerY(), crop.width() / 2.0f,
                    borderPaint);

            float radius = crop.width() / 2.0f;
            canvas.drawLine(crop.centerX() - radius, crop.centerY(),
                    crop.centerX() + radius, crop.centerY(), guidePaint);
            canvas.drawLine(crop.centerX(), crop.centerY() - radius,
                    crop.centerX(), crop.centerY() + radius, guidePaint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            scaleDetector.onTouchEvent(event);
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastX = event.getX();
                    lastY = event.getY();
                    dragging = true;
                    return true;
                case MotionEvent.ACTION_POINTER_DOWN:
                    dragging = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (event.getPointerCount() == 1 && dragging
                            && !scaleDetector.isInProgress()) {
                        float x = event.getX();
                        float y = event.getY();
                        translateX += x - lastX;
                        translateY += y - lastY;
                        lastX = x;
                        lastY = y;
                        clampAndUpdate();
                    }
                    return true;
                case MotionEvent.ACTION_POINTER_UP:
                    dragging = false;
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    dragging = false;
                    performClick();
                    return true;
                default:
                    return true;
            }
        }

        @Override
        public boolean performClick() {
            super.performClick();
            return true;
        }

        Bitmap render(int outputSize) {
            Bitmap result = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.RGB_565);
            Canvas canvas = new Canvas(result);
            canvas.drawColor(Color.BLACK);
            float factor = outputSize / crop.width();
            float scale = currentScale() * factor;
            Matrix outputMatrix = new Matrix();
            outputMatrix.setValues(new float[]{
                    scale, 0, (translateX - crop.left) * factor,
                    0, scale, (translateY - crop.top) * factor,
                    0, 0, 1
            });
            canvas.drawBitmap(bitmap, outputMatrix, bitmapPaint);
            return result;
        }

        private void scaleBy(float factor, float focusX, float focusY) {
            float oldScale = userScale;
            userScale = Math.max(1.0f, Math.min(MAX_USER_SCALE, userScale * factor));
            float applied = userScale / oldScale;
            translateX = focusX - (focusX - translateX) * applied;
            translateY = focusY - (focusY - translateY) * applied;
            clampAndUpdate();
        }

        private void clampAndUpdate() {
            float displayWidth = bitmap.getWidth() * currentScale();
            float displayHeight = bitmap.getHeight() * currentScale();
            translateX = clamp(translateX, crop.right - displayWidth, crop.left);
            translateY = clamp(translateY, crop.bottom - displayHeight, crop.top);
            updateMatrix();
        }

        private void updateMatrix() {
            imageMatrix.setScale(currentScale(), currentScale());
            imageMatrix.postTranslate(translateX, translateY);
            invalidate();
        }

        private float currentScale() {
            return baseScale * userScale;
        }

        private static float clamp(float value, float minimum, float maximum) {
            return Math.max(minimum, Math.min(maximum, value));
        }
    }
}
