package com.example.edgesum.data;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

public class VideoViewModelFactory implements ViewModelProvider.Factory {

    private Application application;
    private VideosRepository repository;

    public VideoViewModelFactory(Application application, VideosRepository repository) {
        this.application = application;
        this.repository = repository;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        return (T) new VideoViewModel(application, repository);
    }
}
