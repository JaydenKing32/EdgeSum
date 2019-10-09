package com.example.myfirstapp.model;

import android.os.Parcel;
import android.os.Parcelable;

import java.math.BigInteger;
import java.util.List;

public class Video implements Parcelable {
    private final String id;
    private final String data;
    private final String name;
    private final BigInteger size;
    private final boolean visible;
    private final String mimeType;

    public static final Creator<Video> CREATOR = new Creator<Video>() {
        @Override
        public Video createFromParcel(Parcel parcel) {
            return new Video(parcel);
        }

        @Override
        public Video[] newArray(int i) {
            return new Video[i];
        }

    };


    public Video(String id, String name, String data, String mimeType, BigInteger size) {
        this.id = id;
        this.data = data;
        this.name = name;
        this.size = size;
        this.mimeType = mimeType;
        this.visible = true;
    }

    public Video(String id, String name, String data, String mimeType, BigInteger size, boolean visible) {
        this.id = id;
        this.data = data;
        this.name = name;
        this.size = size;
        this.mimeType = mimeType;
        this.visible = visible;
    }

    public Video(Video video) {
        this.id = video.id;
        this.data = video.data;
        this.name = video.name;
        this.size = video.size;
        this.mimeType = video.mimeType;
        this.visible = video.visible;
    }

    public Video(Video video, boolean visible) {
        this.id = video.id;
        this.data = video.data;
        this.name = video.name;
        this.size = video.size;
        this.mimeType = video.mimeType;
        this.visible = visible;
    }

    public Video(Parcel in) {
        this.id = in.readString();
        this.data = in.readString();
        this.name = in.readString();
        this.size = new BigInteger(in.readString());
        this.mimeType = in.readString();
        this.visible = in.readByte() != 0;
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

    public boolean isVisible() {
        return visible;
    }

    public String getMimeType() {
        return mimeType;
    }

    @Override
    public String toString() {
        return "Video{" +
                "id='" + id + '\'' +
                ", data='" + data + '\'' +
                ", name='" + name + '\'' +
                ", size=" + size +
                ", visible=" + visible +
                ", mimeType='" + mimeType + '\'' +
                '}';
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(id);
        parcel.writeString(data);
        parcel.writeString(name);
        parcel.writeString(size.toString());
        parcel.writeString(mimeType);
        parcel.writeByte((visible == true) ? (byte) 1 : 0);
    }


}
