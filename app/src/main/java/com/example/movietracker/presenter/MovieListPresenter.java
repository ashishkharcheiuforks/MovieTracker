package com.example.movietracker.presenter;

import android.util.Log;

import com.example.movietracker.R;
import com.example.movietracker.data.entity.MovieResultEntity;
import com.example.movietracker.data.entity.MoviesEntity;
import com.example.movietracker.data.entity.UserEntity;
import com.example.movietracker.data.entity.UserWithFavoriteMovies;
import com.example.movietracker.view.helper.RxDisposeHelper;
import com.example.movietracker.view.model.Filters;
import com.example.movietracker.model.ModelContract;
import com.example.movietracker.view.contract.MovieListView;
import com.example.movietracker.view.model.MovieRecyclerItemPosition;

import java.util.ArrayList;

import io.reactivex.CompletableObserver;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;
import io.reactivex.observers.DisposableObserver;
import io.reactivex.schedulers.Schedulers;

/**
 * Presenter for displaying movies list and favorite movies list
 */
public class MovieListPresenter extends BasePresenter {

    private static final String TAG = MovieListPresenter.class.getCanonicalName();

    private MovieListView view;

    private ModelContract.MovieModel movieModel;
    private ModelContract.UserModel userModel;

    private MoviesEntity moviesEntity = new MoviesEntity();
    private UserEntity userEntity = new UserEntity();
    private Filters filters;
    private MovieRecyclerItemPosition recyclerItemPosition;

    private boolean isActionAllowed;
    private boolean shouldShowFavoriteMoviesList;

    private Disposable userDisposable;
    private Disposable userWithFavoriteMoviesDisposable;
    private Disposable movieDisposable;
    private Disposable moviePageDisposable;
    private Disposable movieListPagesDisposable;

    /**
     * Instantiates a new Movie list presenter.
     *
     * @param view                 the view that implements fragment
     * @param recyclerItemPosition  position and offset of clicked movie, for scrolling purpose on return.
     */
    public MovieListPresenter(
            MovieListView view,
            ModelContract.MovieModel movieModel,
            ModelContract.UserModel userModel,
            Filters filters,
            MovieRecyclerItemPosition recyclerItemPosition) {
        this.view = view;
        this.movieModel = movieModel;
        this.userModel = userModel;
        this.filters = filters;
        this.recyclerItemPosition = recyclerItemPosition;
    }

    /**
     * Initialize.
     *
     * @param shouldShowFavoriteMoviesList  true if showing only favorite movies
     */
    public void initialize(boolean shouldShowFavoriteMoviesList) {
        this.shouldShowFavoriteMoviesList = shouldShowFavoriteMoviesList;
        if (this.shouldShowFavoriteMoviesList) {
            getUserWithFavoriteMovies();
        } else {
            getUser();
            getMoviesByFilters(this.filters);
        }
    }

    /**
     * On movie item clicked.
     * opens movie details fragment
     */
    public void onMovieItemClicked(int itemPosition) {
        int movieId = this.moviesEntity.getMovies().get(itemPosition).getMovieId();
        this.view.showMovieDetailScreen(movieId);
    }

    /**
     * saving position and offset of clicked movie
     */
    public void  saveRecyclerPosition(int itemPosition, int itemOffset) {
        this.setRecyclerItemPosition(itemPosition, itemOffset);
    }

    /**
     * calls when last element of recycler view is completely visible.
     * if isActionAllowed is true then its getting from network/db more 20 movies
     */
    public void lastMovieOfPageReached() {
        if(this.isActionAllowed) {
            this.isActionAllowed = false;
            if (this.moviesEntity.getTotalPages() > this.filters.getPage()) {
                this.filters.incrementPage();
                this.getMoviesWithPagination(this.filters);
            } else {
                showToast(R.string.movie_list_there_are_no_pages);
                this.isActionAllowed = true;
            }
        }
    }

    /**
     * getting movies by filters
     * if there are saved recycler position it gets list of movies for all scrolled pages
     */
    public void getMoviesByFilters(Filters filters) {
        if(this.recyclerItemPosition.getItemPosition() == 0) {
            getMovies(filters);
        } else {
            getMovieListForAllPages(filters);
        }
    }

    @Override
    public void destroy() {
        this.view = null;
        this.recyclerItemPosition.setValuesToZero();
        RxDisposeHelper.dispose(this.userDisposable);
        RxDisposeHelper.dispose(this.userWithFavoriteMoviesDisposable);
        RxDisposeHelper.dispose(this.movieDisposable);
        RxDisposeHelper.dispose(this.moviePageDisposable);
        RxDisposeHelper.dispose(this.movieListPagesDisposable);
    }

    /**
     * On favorite icon clicked. adding or removing movies to/from favorites according to state of isChecked
     *
     * @param itemClickPosition the item click position
     * @param isChecked         state of favoriteToggleButton
     */
    public void onFavoriteChecked(int itemClickPosition, boolean isChecked) {
        MovieResultEntity movie = this.moviesEntity.getMovies().get(itemClickPosition);
        if (movie.isFavorite() != isChecked) {
            movie.setFavorite(isChecked);
            if (isChecked) {
                this.userEntity.addToFavorites(movie);
                addFavoriteMovie(this.userEntity);
            } else {
                this.userEntity.removeFromFavorites(movie);
                deleteMovieFromFavorites(new UserWithFavoriteMovies(movie.getMovieId(), userEntity.getUserId()));
                if (this.userEntity.getFavoriteMovies().isEmpty()) {
                    this.userEntity.setMovieId(-1);
                    updateUser(this.userEntity);
                }
            }
        }
    }

    /**
     * making new call to get movies
     */
    public void onSwipeToRefresh() {
        if (this.userEntity.getFavoriteMovies() == null || this.userEntity.getFavoriteMovies().isEmpty()) {
            displayNothingToShow();
        }

        this.filters.setPage(1);
        if (this.shouldShowFavoriteMoviesList) {
            this.getUserWithFavoriteMovies();
        } else  {
            this.getMoviesByFilters(this.filters);
        }
    }

    private void setRecyclerItemPosition(int movieId, int offset) {
        this.recyclerItemPosition.setItemPosition(movieId);
        this.recyclerItemPosition.setOffset(offset);
    }

    private void scrollToPosition(int itemPosition, int itemOffset) {
        if (this.view != null) {
            this.view.scrollToPositionWithOffset(itemPosition, itemOffset);
        }
    }

    private void scrollToMovieIfPossible(MoviesEntity moviesEntity) {
        if (!moviesEntity.getMovies().isEmpty() && moviesEntity.getMovies().get(recyclerItemPosition.getItemPosition()).getMovieId() != 0) {
            this.scrollToPosition(recyclerItemPosition.getItemPosition(), recyclerItemPosition.getOffset());
        }
    }

    private void renderMovieList(MoviesEntity moviesEntity) {
        if(this.view !=null) {
            this.view.renderMoviesList(moviesEntity);
        }
    }

    private void getMoviesWithPagination(Filters filters) {
        getMoviesByPage(filters);
    }

    private void getMovies(Filters filters) {
        showLoading();

        this.movieDisposable = this.movieModel.getMoviesWithFavorites(filters)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribeWith(new GetMoviesObserver());
    }

    private void getUser() {
        this.userDisposable = this.userModel.getUserWithFavorites()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribeWith(new GetUserObserver());
    }

    private void getUserWithFavoriteMovies() {
        this.userWithFavoriteMoviesDisposable = this.userModel.getUserWithFavorites()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribeWith(new GetUserWithFavoriteMoviesObserver());
    }

    private void updateUser(UserEntity userEntity) {
        this.userModel.updateUser(userEntity)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe(new CompletableUpdateUserObserver());
    }

    private void addFavoriteMovie(UserEntity userEntity) {
        this.userModel.updateUser(userEntity)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe(new CompletableAddFavoriteMovieObserver());
    }

    private void deleteMovieFromFavorites(UserWithFavoriteMovies movieId) {
        this.userModel.deleteUserFromFavorites(movieId)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe(new CompletableDeleteMovieFromFavoritesObserver());
    }

    private void getMoviesByPage(Filters filters) {
        showLoading();

        this.moviePageDisposable = this.movieModel.getMoviesWithFavorites(filters)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribeWith(new GetMoviesPageObserver());
    }

    private void getMovieListForAllPages(Filters filters) {
        showLoading();

        this.movieListPagesDisposable = this.movieModel.getMovieListForPagesWithFavorites(filters)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribeWith(new GetMovieListForPagesObserver());
    }

    private void showLoading() {
        if (view != null) {
            this.view.showLoading();
        }
    }

    private void hideLoading() {
        if (view != null) {
            this.view.hideLoading();
        }
    }

    private void showToast(int resourceId) {
        if (view != null) {
            this.view.showToast(resourceId);
        }
    }

    private void showToast(String text) {
        if(this.view !=null) {
            this.view.showToast(text);
        }
    }

    private void displayNothingToShow() {
        if(this.view !=null) {
            this.view.displayNothingToShowHint();
        }
    }

    private void renderAdditionalMovieListPage(MoviesEntity moviesEntity) {
        if (this.view != null) {
            this.view.renderAdditionalMovieListPage(moviesEntity);
        }

    }

    private void renderMoviesList(MoviesEntity moviesEntity) {
        if (this.view != null) {
            this.view.renderMoviesList(moviesEntity);
        }
    }

    private class GetMoviesObserver extends DisposableObserver<MoviesEntity> {
        @Override
        public void onNext(MoviesEntity moviesEntity) {
            if(moviesEntity == null || moviesEntity.getMovies().isEmpty()) {
                displayNothingToShow();
                MovieListPresenter.this.hideLoading();
                return;
            }
            MovieListPresenter.this.moviesEntity = moviesEntity;
            MovieListPresenter.this.renderMoviesList(moviesEntity);
            MovieListPresenter.this.isActionAllowed = true;
            MovieListPresenter.this.hideLoading();
            MovieListPresenter.this.scrollToMovieIfPossible(moviesEntity);
        }

        @Override
        public void onError(@NonNull Throwable e) {
            MovieListPresenter.this.showToast(R.string.main_error);
            MovieListPresenter.this.hideLoading();
            MovieListPresenter.this.isActionAllowed = true;
        }

        @Override
        public void onComplete() {
            Log.d(TAG, "GetMoviesObserver onComplete");
        }
    }

    private class GetMovieListForPagesObserver extends DisposableObserver<MoviesEntity> {
        @Override
        public void onNext(MoviesEntity moviesEntity) {
            MovieListPresenter.this.moviesEntity = moviesEntity;
            MovieListPresenter.this.renderMoviesList(moviesEntity);
            MovieListPresenter.this.isActionAllowed = true;
            MovieListPresenter.this.hideLoading();
            MovieListPresenter.this.scrollToMovieIfPossible(moviesEntity);
        }

        @Override
        public void onError(@NonNull Throwable e) {
            MovieListPresenter.this.showToast(R.string.main_error);
            MovieListPresenter.this.hideLoading();
            MovieListPresenter.this.isActionAllowed = true;
        }

        @Override
        public void onComplete() {
            Log.d(TAG, "GetMoviesObserver onComplete");

        }
    }

    private class GetMoviesPageObserver extends DisposableObserver<MoviesEntity> {
        @Override
        public void onNext(MoviesEntity moviesEntity) {
            MovieListPresenter.this.moviesEntity.setPage(moviesEntity.getPage());
            MovieListPresenter.this.moviesEntity.setTotalPages(moviesEntity.getTotalPages());
            MovieListPresenter.this.moviesEntity.addMovies(moviesEntity.getMovies());
            MovieListPresenter.this.renderAdditionalMovieListPage(MovieListPresenter.this.moviesEntity);
            MovieListPresenter.this.hideLoading();
            MovieListPresenter.this.isActionAllowed = true;
        }

        @Override
        public void onError(@NonNull Throwable e) {
            MovieListPresenter.this.showToast(R.string.main_error);
            MovieListPresenter.this.hideLoading();
            MovieListPresenter.this.isActionAllowed = true;
            Log.e(TAG, e.getLocalizedMessage());
        }

        @Override
        public void onComplete() {
            Log.d(TAG, "GetMoviesPageObserver onComplete");
        }
    }

    private class GetUserObserver extends DisposableObserver<UserEntity> {

        @Override
        public void onComplete() {
            Log.d(TAG, "onComplete this.userModel.getUser() GetUserObserver");
        }

        @Override
        public void onNext(UserEntity userEntity) {
            MovieListPresenter.this.userEntity = userEntity;
        }

        @Override
        public void onError(Throwable e) {
            MovieListPresenter.this.showToast(R.string.main_error);
            Log.d(TAG, e.getLocalizedMessage());
        }
    }

    private class GetUserWithFavoriteMoviesObserver extends DisposableObserver<UserEntity> {

        @Override
        public void onComplete() {
            Log.d(TAG, "onComplete this.userModel.getUser() GetUserWithFavoriteMoviesObserver");
        }

        @Override
        public void onNext(UserEntity userEntity) {
            MovieListPresenter.this.userEntity = userEntity;

            MovieListPresenter.this.moviesEntity.setMovies(userEntity.getFavoriteMovies());
            MovieListPresenter.this.moviesEntity.setPage(1);
            MovieListPresenter.this.moviesEntity.setTotalPages(1);

            if (moviesEntity.getMovies() == null) {
                displayNothingToShow();
                moviesEntity.setMovies(new ArrayList<>());
                MovieListPresenter.this.renderMovieList(moviesEntity);
            } else {
                MovieListPresenter.this.renderMovieList(moviesEntity);
                MovieListPresenter.this.scrollToMovieIfPossible(moviesEntity);
            }
            MovieListPresenter.this.hideLoading();
        }

        @Override
        public void onError(Throwable e) {
            MovieListPresenter.this.showToast(R.string.main_error);
            MovieListPresenter.this.hideLoading();
            Log.d(TAG, e.getLocalizedMessage());
        }
    }

    private class CompletableUpdateUserObserver implements CompletableObserver {

        @Override
        public void onSubscribe(Disposable d) {
            Log.d(TAG, "Subscribed to this.userModel.updateUser(userEntity) CompletableUpdateUserObserver");
        }

        @Override
        public void onComplete() {
            Log.d(TAG, "onComplete to this.userModel.updateUser(userEntity)");
        }

        @Override
        public void onError(Throwable e) {
            Log.e(TAG, e.getLocalizedMessage());
            MovieListPresenter.this.showToast(R.string.main_error);
        }
    }

    private class CompletableAddFavoriteMovieObserver implements CompletableObserver {

        @Override
        public void onSubscribe(Disposable d) {
            Log.d(TAG, "Subscribed to this.userModel.updateUser CompletableAddFavoriteMovieObserver");
        }

        @Override
        public void onComplete() {
            MovieListPresenter.this.showToast(R.string.movie_added_to_favorite);
        }

        @Override
        public void onError(Throwable e) {
            Log.e(TAG, e.getLocalizedMessage());
            MovieListPresenter.this.showToast(R.string.main_error);
        }
    }

    private class CompletableDeleteMovieFromFavoritesObserver implements CompletableObserver {

        @Override
        public void onSubscribe(Disposable d) {
            Log.d(TAG, "Subscribed to  this.userModel.updateUser(userEntity) CompletableDeleteMovieFromFavoritesObserver");
        }

        @Override
        public void onComplete() {
            MovieListPresenter.this.showToast(R.string.movie_removed_from_favorite);
        }

        @Override
        public void onError(Throwable e) {
            Log.e(TAG, e.getLocalizedMessage());
            MovieListPresenter.this.showToast(R.string.main_error);
        }
    }
}