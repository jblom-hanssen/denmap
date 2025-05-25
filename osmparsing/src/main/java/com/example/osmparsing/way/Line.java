package com.example.osmparsing.way;
import java.io.Serializable;

import javafx.geometry.Point2D;
import javafx.scene.canvas.GraphicsContext;
public class Line implements Serializable{
    float x1, y1, x2, y2;
    public Line(String line) {
        String[] coord = line.split(" ");
        x1 = Float.parseFloat(coord[1]);
        y1 = Float.parseFloat(coord[2]);
        x2 = Float.parseFloat(coord[3]);
        y2 = Float.parseFloat(coord[4]);
    }

    public Line(Point2D p1, Point2D p2) {
        x1 = (float)p1.getX();
        y1 = (float)p1.getY();
        x2 = (float)p2.getX();
        y2 = (float)p2.getY();
    }

    public void draw(GraphicsContext gc) {
        gc.beginPath();
        gc.moveTo(x1, y1);
        gc.lineTo(x2, y2);
        gc.stroke();
    }

}
