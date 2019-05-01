package edu.gvsu.cis.traxy;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.VideoView;

import com.github.clans.fab.FloatingActionButton;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.widget.Autocomplete;
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.kunzisoft.switchdatetime.SwitchDateTimeDialogFragment;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import edu.gvsu.cis.traxy.model.JournalEntry;

public class MediaDetailsActivity extends AppCompatActivity {

    static final double ALLENDALE_LAT = 42.9722;
    static final double ALLENDATE_LNG = -85.9540;

    @BindView(R.id.journal_entry_photo)
    ImageView photoView;
    @BindView(R.id.journal_entry_video)
    VideoView videoView;
    @BindView(R.id.journal_entry_caption)
    TextView entry_caption;
    @BindView(R.id.journal_entry_datetime)
    TextView entry_date;
    @BindView(R.id.journal_entry_loc)
    TextView entry_location;
    @BindView(R.id.fab_save)
    FloatingActionButton fabSave;

    private static SwitchDateTimeDialogFragment dtDialog;
    private static DateTimeFormatter dateFormatter;
    private DatabaseReference entriesRef;
    private Uri dataUri;
    private StorageReference storageRef;
    private Place selectedPlace;
    private int mediaType = 0;

    static {
        dateFormatter = DateTimeFormat.forPattern("MMM d, yyyy HH:mm");
        dtDialog = SwitchDateTimeDialogFragment
                .newInstance("", "OK", "Cancel");
        dtDialog.startAtCalendarView();
        dtDialog.set24HoursMode(true);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_media_details);
        ButterKnife.bind(this);

        entry_date.setText(dateFormatter.print(DateTime.now()));
        dtDialog.setOnButtonClickListener(dateTimeListener);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String uid = user.getUid();
        storageRef = FirebaseStorage.getInstance().getReference().child
                (uid);
        Intent incoming = getIntent();
        if (incoming.hasExtra("FIREBASE_REF")) {
            String fbUrl = incoming.getStringExtra("FIREBASE_REF");
            FirebaseDatabase db = FirebaseDatabase.getInstance();
            entriesRef = db.getReferenceFromUrl(fbUrl);
        }
        if (incoming.hasExtra("PHOTO_URI")) {
            mediaType = 2;
            try {
                dataUri = incoming.getParcelableExtra("PHOTO_URI");
                InputStream istr = getContentResolver().openInputStream
                        (dataUri);
                Bitmap bmp = BitmapFactory.decodeStream(istr);
                photoView.setImageBitmap(bmp);
                istr.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (incoming.hasExtra("VIDEO_URI")) {
            mediaType = 4;
            dataUri = incoming.getParcelableExtra("VIDEO_URI");
            videoView.setVideoURI(dataUri);
            MediaController mc = new MediaController(this);
            videoView.setMediaController(mc);
            photoView.setVisibility(View.INVISIBLE);
            videoView.setVisibility(View.VISIBLE);
        }
    }

    // Replace menu with FAB
    //    @Override
    //    public boolean onCreateOptionsMenu(Menu menu) {
    //        getMenuInflater().inflate(R.menu.media_details, menu);
    //        return true;
    //    }

    private void uploadMedia(int type, String contentType, String
            topDir) {
        DateTime now = DateTime.now();
        DateTimeFormatter fmt = ISODateTimeFormat.dateTime();
        JournalEntry currentEntry = new JournalEntry();
        currentEntry.setCaption(entry_caption.getText().toString());
        currentEntry.setType(type);
        if (selectedPlace != null) {
            currentEntry.setLat(selectedPlace.getLatLng().latitude);
            currentEntry.setLng(selectedPlace.getLatLng().longitude);
        } else {
            // Default to Allendale MI if no place is selected
            currentEntry.setLat(ALLENDALE_LAT);
            currentEntry.setLng(ALLENDATE_LNG);
        }
        currentEntry.setDate(fmt.print(now));

        fabSave.setIndeterminate(true);
        DatabaseReference savedEntry = entriesRef.push();
        savedEntry.setValue(currentEntry);

        StorageMetadata.Builder metaBuilder = new StorageMetadata
                .Builder();
        StorageMetadata meta;
        String mediaName = dataUri.getLastPathSegment();
        meta = metaBuilder.setContentType(contentType).build();
        final StorageReference mediaRef = storageRef.child(topDir + "/" + mediaName);
        mediaRef.putFile(dataUri, meta)
                .continueWithTask(task -> {
                    if (!task.isSuccessful())
                        throw task.getException();
                    return mediaRef.getDownloadUrl();
                })
                .addOnCompleteListener(snapshot -> {
                    Uri uri = snapshot.getResult();
                    savedEntry.child("url").setValue(uri.toString());
                    fabSave.hideProgress();
                    Snackbar.make(entry_caption,
                            "Your media is saved",
                            Snackbar.LENGTH_LONG).show();
                });
        if (mediaType == 4) { // is it a video?
            // Create a thumbnail image
            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            mmr.setDataSource(this, dataUri);
            Bitmap thumb = mmr.getFrameAtTime();
            mmr.release();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            thumb.compress(Bitmap.CompressFormat.JPEG, 100, baos);
            String thumbName = mediaName.replace(".mp4", "-thumb.jpg");
            StorageMetadata.Builder thumbMetaBuilder = new
                    StorageMetadata.Builder();
            StorageMetadata thumbMeta = thumbMetaBuilder
                    .setContentDisposition("image/jpeg").build();
            final StorageReference photoRef = storageRef.child("photos/" + thumbName);
            photoRef.putBytes(baos.toByteArray(), thumbMeta)
                    .continueWithTask(task -> photoRef.getDownloadUrl())
                    .addOnCompleteListener(snapshot2 -> {
                        Uri uri = snapshot2.getResult();
                        savedEntry.child("thumbnailUrl").setValue(uri.toString());
                        fabSave.hideProgress();
                    });
        }
    }

    private void saveMedia() {
        switch (mediaType) {
            case 1: // text
                break;
            case 2: // photo
                uploadMedia(mediaType, "image/jpeg", "photos");
                break;
            case 3: // audio
                break;
            case 4: // video
                uploadMedia(mediaType, "video/mp4", "videos");
                break;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.save_media) {
            saveMedia();
            return true;
        }
        return false;
    }

    @OnClick(R.id.journal_entry_datetime)
    public void datePressed() {
        dtDialog.show(getSupportFragmentManager(), "dt_picker");
    }

    private SwitchDateTimeDialogFragment.OnButtonClickListener dateTimeListener =
            new SwitchDateTimeDialogFragment.OnButtonClickListener() {
                @Override
                public void onPositiveButtonClick(Date date) {
                    //                    DateTime d = new DateTime(date.getTime());
                    entry_date.setText(dateFormatter.print(date.getTime()));
                }

                @Override
                public void onNegativeButtonClick(Date date) {

                }
            };

    @OnClick(R.id.journal_entry_loc)
    public void locationPressed() {
        List<Place.Field> fields = Arrays.asList(Place.Field.ADDRESS, Place.Field.LAT_LNG);
        Intent toPlace = new Autocomplete.IntentBuilder(AutocompleteActivityMode.FULLSCREEN, fields)
                .build(this);
        startActivityForResult(toPlace, 0xD0C);
    }

    @OnClick(R.id.fab_save)
    public void savePressed() {
        View focus = getCurrentFocus();
        if (focus != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService
                    (INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        }
        saveMedia();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            selectedPlace = Autocomplete.getPlaceFromIntent(data);
            entry_location.setText(selectedPlace.getAddress());

        }
    }
}
