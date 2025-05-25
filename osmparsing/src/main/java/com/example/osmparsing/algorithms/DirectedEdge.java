package com.example.osmparsing.algorithms;


public class DirectedEdge implements java.io.Serializable {
    private final int v;        // source vertex
    private final int w;        // target vertex
    private final float weight;// edge weight

    //create a directed edge between v to w
    public DirectedEdge(int v, int w, float weight) {
        this.v = v;
        this.w = w;
        this.weight = weight;
    }

   //return the source vertex
    public int from() {
        return v;
    }

    //return the target vertex
    public int to() {
        return w;
    }

    //Return the edge weight
    public float weight() {
        return weight;
    }


     //Return a string representation of this edge.
    public String toString() {
        return v + "->" + w + " " + String.format("%5.2f", weight);
    }
}