package com.example.myfirstapp.model;

import java.math.BigInteger;

public class Video {
    private final String id;
    private final String data;
    private final String name;
    private final BigInteger size;


    public Video(String id, String name, String data, BigInteger size) {
        this.id = id;
        this.data = data;
        this.name = name;
        this.size = size;
    }

    public Video(Video video) {
        this.id = video.id;
        this.data = video.data;
        this.name = video.name;
        this.size = video.size;
    }

    public String getId() {
        return id;
    }

    public String getData() {
        return data;
    }

    public String getName() {
        return name;
    }

    public BigInteger getSize() {
        return size;
    }

    @Override
    public String toString() {
        return "Video{" +
                "id='" + id + '\'' +
                ", data='" + data + '\'' +
                ", name='" + name + '\'' +
                ", size=" + size +
                '}';
    }
}
