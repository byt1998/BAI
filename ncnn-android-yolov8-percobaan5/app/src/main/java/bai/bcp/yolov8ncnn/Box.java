package bai.bcp.yolov8ncnn;

import android.graphics.Color;
import android.graphics.RectF;

public class Box {
    public float x0,y0,x1,y1;
    private int label;
    private float prob;
    private static String[] labels={"single", "stacked5th"};
    public int colors[][] = {
            { 255,  0, 0},
            {0, 255,   0}
    };
    public Box(float x0,float y0, float x1, float y1, int label, float prob){
        this.x0 = x0;
        this.y0 = y0;
        this.x1 = x1;
        this.y1 = y1;
        this.label = label;
        this.prob = prob;
    }

    public RectF getRect(){
        return new RectF(x0,y0,x1,y1);
    }

    public String getLabel(){
        return labels[label];
    }

    public float getProb(){
        return prob;
    }

    public int getColor() {
        if (label >= 0 && label < colors.length) {
            return Color.rgb(colors[label][0], colors[label][1], colors[label][2]);
        }
        return Color.WHITE;
    }
}

