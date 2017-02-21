package com.boostcamp.hyeon.wallpaper.gallery.view;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import com.boostcamp.hyeon.wallpaper.R;
import com.boostcamp.hyeon.wallpaper.base.app.WallpaperApplication;
import com.boostcamp.hyeon.wallpaper.base.domain.Folder;
import com.boostcamp.hyeon.wallpaper.base.domain.Image;
import com.boostcamp.hyeon.wallpaper.base.domain.Wallpaper;
import com.boostcamp.hyeon.wallpaper.base.util.AlarmManagerHelper;
import com.boostcamp.hyeon.wallpaper.base.util.SharedPreferenceHelper;
import com.boostcamp.hyeon.wallpaper.detail.view.DetailActivity;
import com.boostcamp.hyeon.wallpaper.gallery.adapter.FolderListAdapter;
import com.boostcamp.hyeon.wallpaper.gallery.adapter.ImageListAdapter;
import com.boostcamp.hyeon.wallpaper.gallery.model.GalleryModel;
import com.boostcamp.hyeon.wallpaper.gallery.presenter.FolderListPresenter;
import com.boostcamp.hyeon.wallpaper.gallery.presenter.FolderListPresenterImpl;
import com.boostcamp.hyeon.wallpaper.gallery.presenter.ImageListPresenter;
import com.boostcamp.hyeon.wallpaper.gallery.presenter.ImageListPresenterImpl;
import com.boostcamp.hyeon.wallpaper.base.listener.OnBackKeyPressedListener;
import com.boostcamp.hyeon.wallpaper.main.view.MainActivity;
import com.boostcamp.hyeon.wallpaper.preview.view.PreviewActivity;

import java.util.Calendar;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.realm.Realm;
import io.realm.RealmList;
import io.realm.RealmResults;
import io.realm.Sort;

/**
 * A simple {@link Fragment} subclass.
 */
public class GalleryFragment extends Fragment implements FolderListPresenter.View, ImageListPresenter.View, OnBackKeyPressedListener, RadioGroup.OnCheckedChangeListener{
    private static final String TAG = GalleryFragment.class.getSimpleName();
    private static final int DELAY_MILLIS = 100;
    private static final int MINUTE_CONVERT_TO_MILLIS = 60*1000;
    private static final int HOUR_CONVERT_TO_MILLIS = 60*60*1000;
    @BindView(R.id.rv_folder) RecyclerView mFolderRecyclerView;
    @BindView(R.id.rv_image) RecyclerView mImageRecyclerView;
    private FolderListAdapter mFolderListAdapter;
    private ImageListAdapter mImageListAdapter;
    private FolderListPresenterImpl mFolderListPresenter;
    private ImageListPresenterImpl mImageListPresenter;
    private MenuItem mSelectMenuItem, mPreviewMenuItem, mDoneMenuItem;
    private RadioGroup mChangeScreenRadioGroup, mChangeRepeatCycleRadioGroup;
    private int mRepeatCycle;
    private String mChangeScreenType;

    public GalleryFragment() {
        // Required empty public constructor
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //for use option menu
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_gallery, container, false);
        ButterKnife.bind(this, view);

        init();

        return view;
    }

    public void init(){
        //init Model
        GalleryModel model = new GalleryModel();

        //init Folder Adapter
        Realm realm = WallpaperApplication.getRealmInstance();
        realm.beginTransaction();
        RealmResults<Folder> folderRealmResults = realm.where(Folder.class).findAllSorted("name", Sort.ASCENDING);
        if(folderRealmResults.size() != 0){
            folderRealmResults.get(0).setOpened(true);
        }
        mFolderListAdapter = new FolderListAdapter(
                getContext(),
                folderRealmResults,
                true
        );
        realm.commitTransaction();

        mFolderRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        mFolderRecyclerView.setAdapter(mFolderListAdapter);
        mFolderRecyclerView.setHasFixedSize(true);

        //init Folder Presenter
        mFolderListPresenter = new FolderListPresenterImpl(model);
        mFolderListPresenter.attachView(this);
        mFolderListPresenter.setListAdapterModel(mFolderListAdapter);
        mFolderListPresenter.setListAdapterView(mFolderListAdapter);

        //init Image Adapter
        realm.beginTransaction();
        String bucketId;
        RealmList<Image> imageRealmList = null;
        if(folderRealmResults.size() != 0) {
            bucketId = realm.where(Folder.class).equalTo("isOpened", true).findFirst().getBucketId();
            imageRealmList = realm.where(Folder.class).equalTo("bucketId", bucketId).findFirst().getImages();
        }
        mImageListAdapter = new ImageListAdapter(
                getContext(),
                imageRealmList,
                true
        );
        realm.commitTransaction();

        //init Image(Right) RecyclerView
        mImageRecyclerView.setLayoutManager(new GridLayoutManager(getContext(), 3));
        mImageRecyclerView.setAdapter(mImageListAdapter);
        mImageRecyclerView.setHasFixedSize(true);

        //init Presenter
        mImageListPresenter = new ImageListPresenterImpl(model);
        mImageListPresenter.attachView(this);
        mImageListPresenter.setListAdapterModel(mImageListAdapter);
        mImageListPresenter.setListAdapterView(mImageListAdapter);

        //init SharedPreferences
        SharedPreferenceHelper.getInstance().put(SharedPreferenceHelper.Key.BOOLEAN_GALLEY_SELECT_MODE, false);

        //init initial Value
        mRepeatCycle = 3000;
        mChangeScreenType = getString(R.string.label_wallpaper);
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "on Resume");

    }

    @Override
    public void onPause() {
        Log.d(TAG, "on Pause");
        if(SharedPreferenceHelper.getInstance().getBoolean(SharedPreferenceHelper.Key.BOOLEAN_GALLEY_SELECT_MODE, false)){
            if(!SharedPreferenceHelper.getInstance().getBoolean(SharedPreferenceHelper.Key.BOOLEAN_PREVIEW_ACTIVITY_CALL, false))
                changeModeForDefault();
        }
        super.onPause();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_gallery, menu);
        mSelectMenuItem = menu.findItem(R.id.menu_select);
        mPreviewMenuItem = menu.findItem(R.id.menu_preview);
        mDoneMenuItem = menu.findItem(R.id.menu_done);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case R.id.menu_select:
                changeModeForSelect();
                return true;
            case R.id.menu_preview:
                moveToPreviewActivity();
                return true;
            case R.id.menu_done:
                clickDone();
                return true;
            case android.R.id.home:
                onBack();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBack() {
        if(SharedPreferenceHelper.getInstance().getBoolean(SharedPreferenceHelper.Key.BOOLEAN_GALLEY_SELECT_MODE, false))
            changeModeForDefault();
    }

    @Override
    public void changeModeForSelect() {
        //menu_select menu item gone.
        //menu_preview, menu_done menu item visible.
        mSelectMenuItem.setVisible(false);
        mPreviewMenuItem.setVisible(true);
        mDoneMenuItem.setVisible(true);

        //toolbar title change.
        ((AppCompatActivity)getActivity()).getSupportActionBar().setTitle(getString(R.string.title_select));
        //toolbar home button gone.
        ((AppCompatActivity)getActivity()).getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        //set listener
        ((MainActivity)getActivity()).setOnBackKeyPressedListener(this);
        //set SharedPreferences
        SharedPreferenceHelper.getInstance().put(SharedPreferenceHelper.Key.BOOLEAN_GALLEY_SELECT_MODE, true);
        mImageListAdapter.notifyAdapter();
    }

    @Override
    public void changeModeForDefault() {
        //menu_select menu item visible.
        //menu_preview, menu_done menu item gone.
        mSelectMenuItem.setVisible(true);
        mPreviewMenuItem.setVisible(false);
        mDoneMenuItem.setVisible(false);

        //toolbar title change.
        ((AppCompatActivity)getActivity()).getSupportActionBar().setTitle(getString(R.string.title_gallery));
        //toolbar home button visible.
        ((AppCompatActivity)getActivity()).getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        //set listener
        ((MainActivity)getActivity()).setOnBackKeyPressedListener(null);
        //set SharedPreferences
        SharedPreferenceHelper.getInstance().put(SharedPreferenceHelper.Key.BOOLEAN_GALLEY_SELECT_MODE, false);

        mImageListPresenter.updateAllImagesDeselected();
    }

    @Override
    public void clickFolder(int position) {
        //change adapter
        mImageListAdapter = new ImageListAdapter(
                getContext(),
                mFolderListAdapter.getData().get(position).getImages(),
                true
        );

        mImageRecyclerView.setAdapter(mImageListAdapter);

        mImageListPresenter.detachView();
        mImageListPresenter.attachView(this);
        mImageListPresenter.setListAdapterModel(mImageListAdapter);
        mImageListPresenter.setListAdapterView(mImageListAdapter);
    }

    @Override
    public void moveToDetailActivity(Bundle bundle) {
        Intent intent = new Intent(getActivity(), DetailActivity.class);
        intent.putExtras(bundle);

        getActivity().startActivity(intent);
    }

    private void moveToPreviewActivity(){
        SharedPreferenceHelper.getInstance().put(SharedPreferenceHelper.Key.BOOLEAN_PREVIEW_ACTIVITY_CALL, true);
        Realm realm = WallpaperApplication.getRealmInstance();
        realm.beginTransaction();

        if(realm.where(Image.class).equalTo("isSelected", true).findAll().size() > 0) {
            Intent intent = new Intent(getActivity(), PreviewActivity.class);
            getActivity().startActivity(intent);
        }

        realm.commitTransaction();
    }

    private void registerWallpaper(){
        SharedPreferenceHelper.getInstance().put(SharedPreferenceHelper.Key.LONG_REPEAT_CYCLE_MILLS, (long)mRepeatCycle);
        SharedPreferenceHelper.getInstance().put(SharedPreferenceHelper.Key.STRING_CHANGE_SCREEN_TYPE, mChangeScreenType);
        Realm realm = WallpaperApplication.getRealmInstance();
        realm.beginTransaction();

        RealmResults<Wallpaper> wallpaperRealmResults = realm.where(Wallpaper.class).findAll();
        wallpaperRealmResults.deleteAllFromRealm();

        Wallpaper wallpaper = realm.createObject(Wallpaper.class);
        RealmResults<Image> imageRealmResults = realm.where(Image.class).equalTo("isSelected", true).findAllSorted("number", Sort.ASCENDING);
        RealmList<Image> imageRealmList = new RealmList<>();
        imageRealmList.addAll(imageRealmResults.subList(0, imageRealmResults.size()));
        wallpaper.setImages(imageRealmList);
        wallpaper.setCurrentPosition(0);

        realm.commitTransaction();

        Calendar date = Calendar.getInstance();
        date.setTimeInMillis(System.currentTimeMillis()+DELAY_MILLIS);
        AlarmManagerHelper.unregisterToAlarmManager(getContext());
        AlarmManagerHelper.registerToAlarmManager(getContext(), date);
        changeModeForDefault();
    }

    private void clickDone(){
        Realm realm = WallpaperApplication.getRealmInstance();
        realm.beginTransaction();
        int numberOfSelectedImage = realm.where(Image.class).equalTo("isSelected", true).findAll().size();
        realm.commitTransaction();

        if(numberOfSelectedImage == 0){
            Toast.makeText(getActivity(), "이미지를 선택해주세요!", Toast.LENGTH_SHORT).show();
        }else if(numberOfSelectedImage == 1){
            if(Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                registerWallpaper();
            }else{
                showAlertDialog(numberOfSelectedImage);
            }
        }else if(numberOfSelectedImage > 1){
            showAlertDialog(numberOfSelectedImage);
        }
    }

    private void showAlertDialog(final int numberOfSelectedImage){
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setView(R.layout.item_menu_done_alert);
        builder.setPositiveButton(getString(R.string.label_wallpaper_register), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                registerWallpaper();
            }
        });
        builder.setNegativeButton(getString(R.string.label_cancel), null);
        AlertDialog alertDialog = builder.create();
        alertDialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                if(Build.VERSION.SDK_INT < Build.VERSION_CODES.N && numberOfSelectedImage > 1){
                    //if build version is less than nougat and selected images is greater than one.
                    ((Dialog)dialog).findViewById(R.id.layout_change_screen).setVisibility(View.GONE);
                    mChangeRepeatCycleRadioGroup = (RadioGroup)((Dialog)dialog).findViewById(R.id.rb_change_repeat_cycle);
                    mChangeRepeatCycleRadioGroup.setOnCheckedChangeListener(GalleryFragment.this);
                    mChangeRepeatCycleRadioGroup.check(mChangeRepeatCycleRadioGroup.getChildAt(0).getId());
                    SharedPreferenceHelper.getInstance().put(SharedPreferenceHelper.Key.STRING_CHANGE_SCREEN_TYPE, getString(R.string.label_wallpaper));
                }else if(Build.VERSION.SDK_INT == Build.VERSION_CODES.N && numberOfSelectedImage == 1){
                    //if build version is nougat and selected images is only one.
                    ((Dialog)dialog).findViewById(R.id.layout_change_repeat_cycle).setVisibility(View.GONE);
                    mChangeScreenRadioGroup = (RadioGroup)((Dialog)dialog).findViewById(R.id.rb_change_screen);
                    mChangeScreenRadioGroup.setOnCheckedChangeListener(GalleryFragment.this);
                    mChangeScreenRadioGroup.check(mChangeScreenRadioGroup.getChildAt(0).getId());
                }else{
                    //if build version is nougat and selected selected images is greater than one.
                    mChangeScreenRadioGroup = (RadioGroup)((Dialog)dialog).findViewById(R.id.rb_change_screen);
                    mChangeRepeatCycleRadioGroup = (RadioGroup)((Dialog)dialog).findViewById(R.id.rb_change_repeat_cycle);
                    mChangeScreenRadioGroup.setOnCheckedChangeListener(GalleryFragment.this);
                    mChangeRepeatCycleRadioGroup.setOnCheckedChangeListener(GalleryFragment.this);
                    mChangeScreenRadioGroup.check(mChangeScreenRadioGroup.getChildAt(0).getId());
                    mChangeRepeatCycleRadioGroup.check(mChangeRepeatCycleRadioGroup.getChildAt(0).getId());
                }
            }
        });
        alertDialog.show();
    }

    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {
        RadioButton radioButton = (RadioButton)group.findViewById(checkedId);
        if(radioButton == null)
            return;
        if(group.equals(mChangeScreenRadioGroup) && radioButton.isChecked()){
            mChangeScreenType = radioButton.getText().toString();
        }else if(group.equals(mChangeRepeatCycleRadioGroup) && radioButton.isChecked()){
            String value = radioButton.getText().toString();
            int minute = value.indexOf(getString(R.string.label_minute));
            int hour = value.indexOf(getString(R.string.label_hour));

            if(minute != -1 && hour == -1){
                mRepeatCycle = Integer.valueOf(value.substring(0, minute));
                mRepeatCycle *= MINUTE_CONVERT_TO_MILLIS;
            }else {
                mRepeatCycle = Integer.valueOf(value.substring(0, hour));
                mRepeatCycle *= HOUR_CONVERT_TO_MILLIS;
            }
        }
    }
}