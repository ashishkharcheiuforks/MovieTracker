package com.example.movietracker.view.contract;

import com.example.movietracker.data.entity.movie_details.MovieDetailsEntity;

public interface MovieDetailsView extends DownloadListenerView {
    void renderMovieDetails(MovieDetailsEntity movieDetailsEntity);
    void displayNothingToShowHint();
}
