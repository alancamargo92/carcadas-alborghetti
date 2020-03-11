package com.ukdev.carcadasalborghetti.fragments

import android.content.DialogInterface
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import androidx.annotation.LayoutRes
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.ukdev.carcadasalborghetti.R
import com.ukdev.carcadasalborghetti.adapter.MediaAdapter
import com.ukdev.carcadasalborghetti.handlers.MediaHandler
import com.ukdev.carcadasalborghetti.listeners.QueryListener
import com.ukdev.carcadasalborghetti.listeners.RecyclerViewInteractionListener
import com.ukdev.carcadasalborghetti.model.*
import com.ukdev.carcadasalborghetti.utils.hide
import com.ukdev.carcadasalborghetti.utils.isVisible
import com.ukdev.carcadasalborghetti.utils.show
import com.ukdev.carcadasalborghetti.viewmodel.MediaViewModel
import com.ukdev.carcadasalborghetti.viewmodel.MediaViewModelFactory
import kotlinx.android.synthetic.main.layout_list.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

abstract class MediaListFragment(
        @LayoutRes layoutId: Int,
        private val mediaType: MediaType
) : Fragment(layoutId),
        RecyclerViewInteractionListener,
        OperationsDialogue.Listener,
        DialogInterface.OnDismissListener,
        SwipeRefreshLayout.OnRefreshListener {

    abstract val mediaHandler: MediaHandler

    protected abstract val adapter: MediaAdapter

    private val viewModelFactory by inject<MediaViewModelFactory>()

    private val viewModel by lazy {
        ViewModelProvider(requireActivity(), viewModelFactory).get(MediaViewModel::class.java)
    }

    private var searchView: SearchView? = null

    private lateinit var selectedMedia: Media

    abstract fun showFab()

    abstract fun hideFab()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configureSwipeRefreshLayout()
        configureRecyclerView()
        fetchMedia(mediaType)
        setHasOptionsMenu(true)
        observePlaybackState()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_main, menu)
        searchView = (menu.findItem(R.id.item_search)?.actionView as SearchView).apply {
            queryHint = getString(R.string.search)
        }
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onItemClick(media: Media) {
        lifecycleScope.launch {
            adapter.notifyItemClicked()
            withContext(Dispatchers.IO) {
                mediaHandler.play(media)
            }
            adapter.notifyItemReady()
        }
    }

    override fun onItemLongClick(media: Media) {
        lifecycleScope.launch {
            val operations = viewModel.getAvailableOperations(media)

            if (operations.isOnlyShare()) {
                mediaHandler.share(media)
                adapter.notifyItemReady()
            } else {
                selectedMedia = media
                showOperationsDialogue(operations)
            }
        }
    }

    override fun onOperationSelected(operation: Operation) {
        when (operation) {
            Operation.ADD_TO_FAVOURITES -> viewModel.saveToFavourites(selectedMedia)
            Operation.REMOVE_FROM_FAVOURITES -> viewModel.removeFromFavourites(selectedMedia)
            Operation.SHARE -> lifecycleScope.launch {
                mediaHandler.share(selectedMedia)
            }
        }

        adapter.notifyItemReady()
    }

    override fun onDismiss(dialogue: DialogInterface?) {
        adapter.notifyItemReady()
    }

    override fun onRefresh() {
        swipe_refresh_layout.isRefreshing = true
        fetchMedia(mediaType)
    }

    private fun configureSwipeRefreshLayout() = with(swipe_refresh_layout) {
        setOnRefreshListener(this@MediaListFragment)
        setColorSchemeResources(R.color.red, R.color.black)
    }

    private fun configureRecyclerView() {
        recycler_view.adapter = adapter.apply { setListener(this@MediaListFragment) }
    }

    private fun fetchMedia(mediaType: MediaType) {
        group_error.hide()
        showProgressBar()

        if (mediaType == MediaType.BOTH) fetchFavourites()
        else fetchAudiosOrVideos()
    }

    private fun fetchFavourites() {
        lifecycleScope.launch {
            when (val result = viewModel.getFavourites()) {
                is Success<LiveData<List<Media>>> -> observeFavourites(result.body)
                is GenericError -> showError(ErrorType.UNKNOWN)
            }
        }
    }

    private fun fetchAudiosOrVideos() {
        lifecycleScope.launch {
            viewModel.getMedia(mediaType).observe(viewLifecycleOwner, Observer { result ->
                when (result) {
                    is Success<List<Media>> -> displayMedia(result.body)
                    is GenericError -> showError(ErrorType.UNKNOWN)
                    is NetworkError -> showError(ErrorType.CONNECTION)
                }
            })
        }
    }

    private fun observeFavourites(favouritesLiveData: LiveData<List<Media>>) {
        favouritesLiveData.observe(viewLifecycleOwner, Observer { favourites ->
            displayMedia(favourites)
        })
    }

    private fun observePlaybackState() {
        mediaHandler.isPlaying().observe(viewLifecycleOwner, Observer { isPlaying ->
            if (isPlaying)
                showFab()
            else
                hideFab()
        })
    }

    private fun showProgressBar() {
        recycler_view.hide()
        progress_bar.show()
    }

    private fun displayMedia(media: List<Media>) {
        hideErrorIfVisible()

        if (media.isEmpty()) {
            showError(ErrorType.NO_FAVOURITES)
        } else {
            adapter.submitData(media)
            searchView?.setOnQueryTextListener(QueryListener(adapter, media))
            hideProgressBar()
        }
    }

    private fun hideErrorIfVisible() {
        with(group_error) {
            if (isVisible())
                hide()
        }

        with(bt_try_again) {
            if (isVisible())
                hide()
        }
    }

    private fun showError(errorType: ErrorType) {
        progress_bar.hide()
        recycler_view.hide()
        group_error.show()

        if (errorType != ErrorType.NO_FAVOURITES) {
            with(bt_try_again) {
                show()
                setOnClickListener {
                    it.hide()
                    fetchAudiosOrVideos()
                }
            }
        }

        val icon = errorType.iconRes
        val text = errorType.textRes

        img_error.setImageResource(icon)
        txt_error.setText(text)
    }

    private fun hideProgressBar() {
        progress_bar.hide()
        recycler_view.show()
        if (swipe_refresh_layout.isRefreshing)
            swipe_refresh_layout.isRefreshing = false
    }

    private fun showOperationsDialogue(operations: List<Operation>) {
        OperationsDialogue.newInstance(operations).apply {
            setOnOperationSelectedListener(this@MediaListFragment)
        }.show(childFragmentManager, null)
    }

    private fun List<Operation>.isOnlyShare(): Boolean {
        return size == 1 && first() == Operation.SHARE
    }

}