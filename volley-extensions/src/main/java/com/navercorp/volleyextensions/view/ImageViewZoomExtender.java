package com.navercorp.volleyextensions.view;

import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;

import com.navercorp.volleyextensions.util.Assert;

class ImageViewZoomExtender implements ZoomableComponent {
	public static final int DEFAULT_MATRIX_SIZE = 3 * 3;
	public static final int NONE_DEF_STYLE = 0;
	public static final float ORIGINAL_LEVEL = 1.0f;
	private static final PointF topLeftPoint = new PointF(0.0f, 0.0f);

	private float zoomLevel = ORIGINAL_LEVEL;
	private ImageView imageView;
	private final Matrix currentMatrix = new Matrix();
	private float initialScaleSize;

	private int viewWidth;
	private int viewHeight;
	private int imageWidth;
	private int imageHeight;

	public ImageViewZoomExtender(ImageView imageView) {
		Assert.notNull(imageView, "ImageView must not be null.");
		setImageView(imageView);
		setScaleTypeMatrix();
	}

	private void setImageView(ImageView imageView) {
		this.imageView = imageView;
	}

	private void setScaleTypeMatrix() {
		imageView.setScaleType(ScaleType.MATRIX);
	}
	@Override
	public float getZoomLevel() {
		return zoomLevel;
	}

	@Override
	public void restore(ZoomInfo zoomInfo) {
		if (isImageEmpty()) {
			return;
		}

		zoomInfo = createZoomInfoIfNull(zoomInfo);

		currentMatrix.reset();
		saveCurrentSizes();

		initializeScaleSize();

		float oldZoomLevel = zoomInfo.getZoomLevel();
		float oldTranslateX = zoomInfo.getTranslateX();
		float oldTranslateY = zoomInfo.getTranslateY();

		float targetLevel = oldZoomLevel;
		zoomTo(targetLevel);
		panTo(oldTranslateX * initialScaleSize, oldTranslateY * initialScaleSize);	
	}

	private static ZoomInfo createZoomInfoIfNull(ZoomInfo zoomInfo) {
		if (zoomInfo == null) {
			zoomInfo = new ZoomInfo();
		}
		return zoomInfo;
	}

	@Override
	public ZoomInfo save() {
		return createCurrentZoomInfo();
	}

	private ZoomInfo createCurrentZoomInfo() {
		float[] values = getValuesOfMatrix(currentMatrix);
		return new ZoomInfo(zoomLevel, new PointF(values[Matrix.MTRANS_X] / initialScaleSize, values[Matrix.MTRANS_Y] / initialScaleSize));
	}

	private void initializeScaleSize() {
		if (isImageSizeLargerThanView()) {
			initialScaleSize = computeFitScaleOfImage();
		} else {
			initialScaleSize = ORIGINAL_LEVEL;
		}
		
		scaleSize(currentMatrix, initialScaleSize, 0,0);
	}

	private boolean isImageSizeLargerThanView() {
		return (viewWidth < imageWidth || viewHeight < imageHeight);
	}

	private void saveCurrentSizes() {
		if(isImageEmpty()) {
			return;
		}

		Drawable drawable = imageView.getDrawable();
		viewWidth = imageView.getMeasuredWidth();
		viewHeight = imageView.getMeasuredHeight();
		imageWidth = drawable.getIntrinsicWidth();
		imageHeight = drawable.getIntrinsicHeight();
	}

	private float computeFitScaleOfImage() {
		float xRatio = (float)viewWidth / (float)imageWidth;
		float yRatio = (float)viewHeight / (float)imageHeight;

		// Math.min fits the image to the shorter axis. (with letterboxes around)
		// Math.max fits the image to the longer axis. (with the other axis cropped)
		return Math.min(xRatio, yRatio);
	}

	public void zoomTo(float targetLevel) {
		zoomTo(targetLevel, topLeftPoint);
	}

	public void zoomTo(float targetLevel, PointF zoomPoint) {
		Assert.notNull(zoomPoint, "The zoom point must not be null.");
		zoomTo(targetLevel, zoomPoint.x, zoomPoint.y);
	}

	@Override
	public void zoomTo(float targetLevel, float zoomX, float zoomY) {
		if(isImageEmpty()) {
			return;
		}

		scaleSize(currentMatrix, targetLevel, zoomX, zoomY);
		boundArea(currentMatrix);
		centerImage(currentMatrix);
		setImageMatrix(currentMatrix);
		updateZoomLevel(targetLevel);
	}

	private void updateZoomLevel(float targetLevel) {
		zoomLevel = targetLevel;
	}

	private void scaleSize(Matrix matrix, float targetLevel, float zoomX, float zoomY) {
		float newScale = targetLevel / zoomLevel;
		matrix.postScale(newScale, newScale, zoomX, zoomY);
	}

	private void centerImage(Matrix matrix) {
		float[] values = getValuesOfMatrix(matrix);
		float scaledImageWidth = imageWidth * values[Matrix.MSCALE_X], scaledImageHeight = imageHeight * values[Matrix.MSCALE_Y];
		float translateX = values[Matrix.MTRANS_X], translateY = values[Matrix.MTRANS_Y];
		if (scaledImageWidth < viewWidth) {
			translateX = (float)viewWidth / 2 - (float)scaledImageWidth / 2;
		}
		if (scaledImageHeight < viewHeight) {
			translateY = (float)viewHeight / 2 - (float)scaledImageHeight / 2;
		}

		values[Matrix.MTRANS_X] = translateX;
		values[Matrix.MTRANS_Y] = translateY;

		// Use setValues instead of setTranslate() because when setTranslate() is called, rest of values in a matrix is reset too.  
		matrix.setValues(values);
	}

	private boolean isImageEmpty() {
		return imageView.getDrawable() == null;
	}

	public void panTo(PointF difference) {
		Assert.notNull(difference, "The target point must not be null.");
		panTo(difference.x, difference.y);
	}

	@Override
	public void panTo(float dx, float dy) {
		if(isImageEmpty()) {
			return;
		}

		translatePosition(currentMatrix, dx, dy);
		boundArea(currentMatrix);
		centerImage(currentMatrix);
		setImageMatrix(currentMatrix);
	}

	private void setImageMatrix(Matrix matrix) {
		imageView.setImageMatrix(matrix);
	}

	private void translatePosition(Matrix matrix, float dx, float dy) {
		float[] values = getValuesOfMatrix(matrix);
		values[Matrix.MTRANS_X] += dx;
		values[Matrix.MTRANS_Y] += dy;
		matrix.setValues(values);
	}

	protected void boundArea(Matrix matrix) {
		float[] values = getValuesOfMatrix(matrix);

		float scaledImageWidth = imageWidth * values[Matrix.MSCALE_X], scaledImageHeight = imageHeight * values[Matrix.MSCALE_Y];
		float translateX = values[Matrix.MTRANS_X], translateY = values[Matrix.MTRANS_Y];

		// don't let the image go outside
		if (translateX < viewWidth - scaledImageWidth) {
			translateX = viewWidth - scaledImageWidth;
		}
		if (translateY < viewHeight - scaledImageHeight) {
			translateY = viewHeight - scaledImageHeight;
		}
		if (translateX > 0.0f) {
			translateX = 0.0f;
		}
		if (translateY > 0.0f) {
			translateY = 0.0f;
		}
		
		values[Matrix.MTRANS_X] = translateX;
		values[Matrix.MTRANS_Y] = translateY;
		// Use setValues instead of setTranslate() because when setTranslate() is called, rest of values in a matrix is reset too.  
		matrix.setValues(values);
	}

	private static float[] getValuesOfMatrix(Matrix matrix) {
		float[] values = new float[DEFAULT_MATRIX_SIZE];
		matrix.getValues(values);
		return values;
	}	
}