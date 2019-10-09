package com.example.myfirstapp.page.main;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myfirstapp.R;
import com.example.myfirstapp.data.VideoViewModel;
import com.example.myfirstapp.data.VideoViewModelFactory;
import com.example.myfirstapp.data.VideosRepository;
import com.example.myfirstapp.model.Video;
import com.example.myfirstapp.util.video.videoeventhandler.VideoEventHandler;
import com.example.myfirstapp.util.video.viewholderprocessor.VideoViewHolderProcessor;

import org.greenrobot.eventbus.EventBus;

import java.util.List;

/**
 * A fragment representing a list of Items.
 * <p/>
 * Activities containing this fragment MUST implement the {@link OnListFragmentInteractionListener}
 * interface.
 */
public class VideoFragment extends Fragment {

    // TODO: Customize parameter argument names
    private static final String ARG_COLUMN_COUNT = "column-count";
    // TODO: Customize parameters
    private int mColumnCount = 1;

    private OnListFragmentInteractionListener mListener;

    private VideoViewHolderProcessor videoViewHolderProcessor;

    private ActionButton actionButton;

    private VideosRepository repository;


    private VideoViewModel videoViewModel;

    private String TAG = VideoFragment.class.getSimpleName();


    private VideoEventHandler videoEventHandler;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public VideoFragment() {
    }

    public static VideoFragment newInstance(int columnCount,
                                            VideoViewHolderProcessor videoViewHolderProcessor,
                                            ActionButton actionButton,
                                            VideoEventHandler handler) {
        VideoFragment fragment = new VideoFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_COLUMN_COUNT, columnCount);
        fragment.setArguments(args);
        fragment.videoViewHolderProcessor = videoViewHolderProcessor;
        fragment.actionButton = actionButton;
        fragment.videoEventHandler = handler;
        return fragment;
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            mColumnCount = getArguments().getInt(ARG_COLUMN_COUNT);
        }

        setVideoViewModel(
                ViewModelProviders.of(
                        this,
                        new VideoViewModelFactory(
                                this.getActivity().getApplication(),
                                repository))
                        .get(VideoViewModel.class)
        );

    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_videos_list, container, false);

        EventBus.getDefault().register(videoEventHandler);

        // Set the adapter
        if (view instanceof RecyclerView) {
            Context context = view.getContext();
            RecyclerView recyclerView = (RecyclerView) view;
            if (mColumnCount <= 1) {
                recyclerView.setLayoutManager(new LinearLayoutManager(context));
            } else {
                recyclerView.setLayoutManager(new GridLayoutManager(context, mColumnCount));
            }

            final VideoRecyclerViewAdapter adapter = new VideoRecyclerViewAdapter(
                    mListener,
                    this.getContext(),
                    this.actionButton.getText(),
                    videoViewHolderProcessor,
                    videoViewModel);
            recyclerView.setAdapter(adapter);
            videoViewModel.getVideos().observe(getActivity(), new Observer<List<Video>>() {
                @Override
                public void onChanged(List<Video> videos) {
                    Log.i(TAG, "onChanged for Videos");
                    adapter.setVideos(videos);
                }
            });
        }
        return view;
    }


    @Override
    public void onAttach(Context context) {
        super.onAttach(context);


        if (context instanceof OnListFragmentInteractionListener) {
            mListener = (OnListFragmentInteractionListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnListFragmentInteractionListener");
        }


    }

    @Override
    public void onDetach() {
        EventBus.getDefault().unregister(videoEventHandler);
        super.onDetach();
        mListener = null;
    }

    public void setRepository(VideosRepository repository) {
        this.repository = repository;
    }

    private void setVideoViewModel(VideoViewModel videoViewModel) {
        this.videoViewModel = videoViewModel;

    }


    public void setVideoEventHandler(VideoEventHandler handler) {
        this.videoEventHandler = handler;
    }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p/>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface OnListFragmentInteractionListener {
        // TODO: Update argument type and name
        void onListFragmentInteraction(Video item);
    }
}
