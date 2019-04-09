package com.example.movietracker.data.repository;

import com.example.movietracker.data.database.MoviesDatabase;
import com.example.movietracker.data.database.dao.GenresDao;
import com.example.movietracker.data.database.dao.MovieDao;
import com.example.movietracker.data.database.dao.MovieDetailDao;
import com.example.movietracker.data.entity.Filters;
import com.example.movietracker.data.entity.entity_mapper.MovieCastsDataMapper;
import com.example.movietracker.data.entity.entity_mapper.MovieReviewsDataMapper;
import com.example.movietracker.data.entity.entity_mapper.MovieVideosDataMapper;
import com.example.movietracker.data.entity.genre.GenreEntity;
import com.example.movietracker.data.entity.genre.GenresEntity;
import com.example.movietracker.data.entity.movie_details.cast.MovieCastsEntity;
import com.example.movietracker.data.entity.movie_details.MovieDetailsEntity;
import com.example.movietracker.data.entity.movie_details.review.MovieReviewsEntity;
import com.example.movietracker.data.entity.movie_details.video.MovieVideosEntity;
import com.example.movietracker.data.entity.MoviesEntity;
import com.example.movietracker.data.net.RestClient;
import com.example.movietracker.data.net.api.MovieApi;

import java.util.List;

import io.reactivex.Observable;
import io.reactivex.Single;

public class MovieDataRepository implements MovieRepository {

    private MovieApi movieApi;
    private GenresDao genresDao;
    private MovieDao movieDao;
    private MovieDetailDao movieDetailDao;

    private MovieVideosDataMapper movieVideosDataMapper;
    private MovieCastsDataMapper movieCastsDataMapper;
    private MovieReviewsDataMapper movieReviewsDataMapper;

    private MovieDataRepository() {}

    private static class SingletonHelper {
        private static final MovieDataRepository INSTANCE = new MovieDataRepository();
    }

    public static MovieDataRepository getInstance(){
        return SingletonHelper.INSTANCE;
    }

    @Override
    public void init(RestClient restClient, MoviesDatabase moviesDatabase) {
        this.movieApi = restClient.getMovieApi();
        this.genresDao = moviesDatabase.genresDao();
        this.movieDao = moviesDatabase.movieDao();
        this.movieDetailDao = moviesDatabase.movieDetailDao();
        this.movieCastsDataMapper = new MovieCastsDataMapper();
        this.movieVideosDataMapper = new MovieVideosDataMapper();
        this.movieReviewsDataMapper = new MovieReviewsDataMapper();
    }

    @Override
    public Single<GenresEntity> getGenres() {
        return this.movieApi.getGenres()
                        .doOnSuccess(genresEntity -> this.genresDao.saveGenres(genresEntity.getGenres()))
                        .onErrorResumeNext(this.genresDao.getAllGenres().map(GenresEntity::new));
    }

    @Override
    public Single<GenresEntity> getLocalGenres() {
        return this.genresDao.getAllGenres().map(genreEntities -> new GenresEntity())
                .onErrorResumeNext(this.movieApi.getGenres()
                                .doOnSuccess(genresEntity -> this.genresDao.saveGenres(genresEntity.getGenres())));
    }

    /*  return  this.movieApi.getGenres().doOnNext(genresEntity -> this.genresDao.saveGenres(genresEntity.getGenres()))
                .flatMap(genresEntity -> Observable.create( emitter -> emitter.onNext(genresDao.getAllGenres().map(GenresEntity::new).blockingFirst())))
                .onErrorResumeNext((error) -> { return Observable.error(BadRequestExceptionMaker.makeCustomExceptionIfBadRequestOrReturnOriginal(error, NoNetworkException.class));})
                .flatMap(genresEntity -> Observable.create( emitter -> emitter.onNext(genresDao.getAllGenres().map(GenresEntity::new).blockingFirst())));*/

    /* return  this.genresDao.getAllGenres().map(GenresEntity::new).flatMap(
                genreEntities -> this.movieApi.getGenres())
                 .doOnNext(genres -> this.genresDao.saveGenres(genres.getGenres()));*/

    /* return  this.movieApi.getGenres().doOnNext(genresEntity -> this.genresDao.saveGenres(genresEntity.getGenres()))
                .onErrorResumeNext((error) -> { return Observable.error(BadRequestExceptionMaker.makeCustomExceptionIfBadRequestOrReturnOriginal(error, NoNetworkException.class));})
            .flatMap( (genresEntity) -> { return Observable.just(genresDao.getAllGenres().map(GenresEntity::new)).blockingFirst();});*/

        /* return  this.movieApi.getGenres().doOnNext(genresEntity -> this.genresDao.saveGenres(genresEntity.getGenres()))
                .onExceptionResumeNext(Observable.concat(genresDao.getAllGenres().map(GenresEntity::new), Observable.error(new IOException())).share());*/

    /* return  this.movieApi.getGenres().doOnNext(genresEntity -> this.genresDao.saveGenres(genresEntity.getGenres()))
                .onExceptionResumeNext(genresDao.getAllGenres().map(GenresEntity::new));*/

    @Override
    public Observable<MoviesEntity> getMovies(Filters filters) {
        return this.movieApi.getMovies(
                filters.getCommaSeparatedGenres(),
                filters.getSortBy()
                        .concat(".").concat(
                                filters.getOrder().name().toLowerCase()),
                filters.getPage(),
                filters.isIncludeAdult())
                .doOnNext(moviesEntity -> {
                    this.movieDao.saveMovies(moviesEntity.getMovies());
                    this.movieDao.addMovieGenreRelation(moviesEntity.getMovies());
                })
                .onExceptionResumeNext(
                        this.movieDao.getMoviesByOptions(filters.getSelectedGenresIds())
                                .map(movieResultEntities ->
                                        new MoviesEntity(0,0,movieResultEntities))
                );
    }

    @Override
    public Observable<MovieDetailsEntity> getMovieDetails(int movieId) {
        return this.movieApi.getMovieDetailsById(movieId)
                .doOnNext(movieDetailsEntity -> {
                    movieDetailDao.saveInfo(movieDetailsEntity);
                })
                .onExceptionResumeNext(
                        movieDetailDao.getMovieInfo(movieId).map(movieDetailsEntity -> {
                    List<GenreEntity> genreEntities = movieDetailDao.getGenres(movieId).blockingFirst();
                    movieDetailsEntity.setGenres(genreEntities);
                    return movieDetailsEntity;
                }));
    }

    @Override
    public Observable<MovieCastsEntity> getMovieCasts(int movieId) {
        return this.movieApi.getMovieCasts(movieId)
                .doOnNext(movieCastsEntity -> this.movieDetailDao.saveCasts(
                        this.movieCastsDataMapper.transformToList(movieCastsEntity))
                ).onExceptionResumeNext(
                        this.movieDetailDao.getCasts(movieId)
                                .map(this.movieCastsDataMapper::transformFromList)
                );
    }

    @Override
    public Observable<MovieVideosEntity> getMovieVideos(int movieId) {
        return this.movieApi.getMovieVideos(movieId)
                .doOnNext(movieVideosEntity -> this.movieDetailDao.saveMovieVideos(
                        this.movieVideosDataMapper.transformToList(movieVideosEntity))
                ).onExceptionResumeNext(
                        this.movieDetailDao.getMovieVideos(movieId)
                                .map(this.movieVideosDataMapper::transformFromList)
                );
    }

    @Override
    public Observable<MovieReviewsEntity> getMovieReviews(int movieId) {
        return this.movieApi.getMovieReviews(movieId)
                .doOnNext(movieReviewsEntity -> this.movieDetailDao.saveMovieReviews(
                        this.movieReviewsDataMapper.transformToList(movieReviewsEntity))
                ).onExceptionResumeNext(
                        this.movieDetailDao.getMovieReviews(movieId)
                                .map(this.movieReviewsDataMapper::transformFromList)
                );
    }
}
