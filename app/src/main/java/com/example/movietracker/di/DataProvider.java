package com.example.movietracker.di;

import android.app.AlertDialog;

import com.example.movietracker.AndroidApplication;
import com.example.movietracker.data.entity.MovieRequestEntity;
import com.example.movietracker.data.entity.genre.GenresEntity;
import com.example.movietracker.data.entity.MoviesEntity;
import com.example.movietracker.view.custom_view.FilterAlertDialog;

public class DataProvider {

    public static GenresEntity genresEntity;
    public static MovieRequestEntity movieRequestEntity;

    public static void initialize() {
        genresEntity = new GenresEntity();
        movieRequestEntity = new MovieRequestEntity();
    }

    public static void onDestroy() {
        genresEntity = null;
        movieRequestEntity = null;
    }
}
