package vn.edu.ueh.thudinh.fashion2hand;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.View;

/** Static illustrative map used by the local delivery tracking prototype. */
public class DeliveryMapView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;

    public DeliveryMapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        canvas.drawColor(Color.rgb(232, 238, 232));

        paint.setColor(Color.rgb(244, 247, 243));
        paint.setStrokeWidth(dp(1));
        for (float x = -h; x < w + h; x += dp(35)) canvas.drawLine(x, 0, x + h * .18f, h, paint);
        for (float y = dp(20); y < h; y += dp(43)) canvas.drawLine(0, y, w, y - h * .08f, paint);

        paint.setColor(Color.WHITE);
        paint.setStrokeWidth(dp(12));
        Path roadOne = new Path();
        roadOne.moveTo(-dp(20), h * .27f);
        roadOne.cubicTo(w * .25f, h * .38f, w * .58f, h * .53f, w + dp(25), h * .31f);
        canvas.drawPath(roadOne, paint);
        Path roadTwo = new Path();
        roadTwo.moveTo(w * .05f, h * .8f);
        roadTwo.cubicTo(w * .35f, h * .65f, w * .6f, h * .18f, w * 1.05f, h * .05f);
        canvas.drawPath(roadTwo, paint);

        paint.setColor(Color.rgb(16, 69, 54));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2.5f));
        paint.setPathEffect(new android.graphics.DashPathEffect(new float[]{dp(5), dp(4)}, 0));
        Path route = new Path();
        route.moveTo(w * .24f, h * .72f);
        route.cubicTo(w * .45f, h * .68f, w * .47f, h * .66f, w * .63f, h * .58f);
        route.cubicTo(w * .8f, h * .49f, w * .77f, h * .43f, w * .76f, h * .34f);
        route.cubicTo(w * .75f, h * .27f, w * .72f, h * .23f, w * .72f, h * .17f);
        canvas.drawPath(route, paint);
        paint.setPathEffect(null);
        paint.setStyle(Paint.Style.FILL);

        drawPin(canvas, w * .24f, h * .72f, Color.rgb(16, 69, 54), dp(6));
        drawPin(canvas, w * .76f, h * .34f, Color.rgb(242, 124, 91), dp(8));
        drawPin(canvas, w * .72f, h * .17f, Color.rgb(242, 124, 91), dp(7));
    }

    private void drawPin(Canvas canvas, float x, float y, int color, float radius) {
        paint.setColor(Color.WHITE);
        canvas.drawCircle(x, y, radius + dp(2), paint);
        paint.setColor(color);
        canvas.drawCircle(x, y, radius, paint);
    }

    private float dp(float value) { return value * density; }
}
