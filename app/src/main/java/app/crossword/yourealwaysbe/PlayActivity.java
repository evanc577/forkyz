package app.crossword.yourealwaysbe;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;

import app.crossword.yourealwaysbe.forkyz.ForkyzApplication;
import app.crossword.yourealwaysbe.forkyz.R;
import app.crossword.yourealwaysbe.puz.Clue;
import app.crossword.yourealwaysbe.puz.MovementStrategy;
import app.crossword.yourealwaysbe.puz.Playboard.Word;
import app.crossword.yourealwaysbe.puz.Playboard;
import app.crossword.yourealwaysbe.puz.Puzzle.Position;
import app.crossword.yourealwaysbe.puz.Puzzle;
import app.crossword.yourealwaysbe.util.KeyboardManager;
import app.crossword.yourealwaysbe.util.files.FileHandler;
import app.crossword.yourealwaysbe.view.ClueTabs;
import app.crossword.yourealwaysbe.view.ForkyzKeyboard;
import app.crossword.yourealwaysbe.view.PlayboardRenderer;
import app.crossword.yourealwaysbe.view.ScrollingImageView.ClickListener;
import app.crossword.yourealwaysbe.view.ScrollingImageView.Point;
import app.crossword.yourealwaysbe.view.ScrollingImageView.ScaleListener;
import app.crossword.yourealwaysbe.view.ScrollingImageView;

import java.util.logging.Logger;

public class PlayActivity extends PuzzleActivity
                          implements Playboard.PlayboardListener,
                                     ClueTabs.ClueTabsListener {
    private static final Logger LOG = Logger.getLogger("app.crossword.yourealwaysbe");
    private static final double BOARD_DIM_RATIO = 1.0;
    private static final String SHOW_CLUES_TAB = "showCluesOnPlayScreen";
    private static final String CLUE_TABS_PAGE = "playActivityClueTabsPage";
    private static final String PREF_SHOW_ERRORS_GRID = "showErrors";
    private static final String PREF_SHOW_ERRORS_CURSOR = "showErrorsCursor";
    static final String ALPHA = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    public static final String SHOW_TIMER = "showTimer";
    public static final String SCALE = "scale";

    private ClueTabs clueTabs;
    private ConstraintLayout constraintLayout;
    private Handler handler = new Handler(Looper.getMainLooper());
    private KeyboardManager keyboardManager;
    private MovementStrategy movement = null;
    private ScrollingImageView boardView;
    private CharSequence boardViewDescriptionBase;
    private TextView clue;
    private PlayboardRenderer renderer;

    private long lastTap = 0;
    private int screenWidthInInches;
    private Runnable fitToScreenTask = new Runnable() {
        @Override
        public void run() {
            fitToScreen();
        }
    };

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        metrics = getResources().getDisplayMetrics();
        this.screenWidthInInches = (metrics.widthPixels > metrics.heightPixels ? metrics.widthPixels : metrics.heightPixels) / Math.round(160 * metrics.density);
        LOG.info("Configuration Changed "+this.screenWidthInInches+" ");
        if(this.screenWidthInInches >= 7){
            this.handler.post(this.fitToScreenTask);
        }
    }

    DisplayMetrics metrics;

    /**
     * Create the activity
     *
     * This only sets up the UI widgets. The set up for the current
     * puzzle/board is done in onResume as these are held by the
     * application and may change while paused!
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.play);

        metrics = getResources().getDisplayMetrics();
        this.screenWidthInInches = (metrics.widthPixels > metrics.heightPixels ? metrics.widthPixels : metrics.heightPixels) / Math.round(160 * metrics.density);

        try {
            if (!prefs.getBoolean(SHOW_TIMER, false)) {
                if (ForkyzApplication.isLandscape(metrics)) {
                    if (ForkyzApplication.isMiniTabletish(metrics)) {
                        utils.hideWindowTitle(this);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        utils.holographic(this);
        utils.finishOnHomeButton(this);

        setDefaultKeyMode(Activity.DEFAULT_KEYS_DISABLE);

        MovementStrategy movement = getMovementStrategy();

        setFullScreenMode();

        // board is loaded by BrowseActivity and put into the
        // Application, onResume sets up PlayActivity for current board
        // as it may change!
        Playboard board = getBoard();
        Puzzle puz = getPuzzle();

        if (board == null || puz == null) {
            LOG.info("PlayActivity started but no Puzzle selected, finishing.");
            finish();
            return;
        }

        setContentView(R.layout.play);

        this.constraintLayout
            = (ConstraintLayout) this.findViewById(R.id.playConstraintLayout);

        this.clue = this.findViewById(R.id.clueLine);
        if (clue != null && clue.getVisibility() != View.GONE) {
            ConstraintSet set = new ConstraintSet();
            set.clone(constraintLayout);
            set.setVisibility(clue.getId(), ConstraintSet.GONE);
            set.applyTo(constraintLayout);

            View custom = utils.onActionBarCustom(this, R.layout.clue_line_only);
            if (custom != null) {
                clue = custom.findViewById(R.id.clueLine);
            }
        }

        this.boardView = (ScrollingImageView) this.findViewById(R.id.board);
        this.boardViewDescriptionBase = this.boardView.getContentDescription();
        this.clueTabs = this.findViewById(R.id.playClueTab);

        ForkyzKeyboard keyboardView
            = (ForkyzKeyboard) this.findViewById(R.id.keyboard);
        keyboardManager
            = new KeyboardManager(this, keyboardView, null);
        keyboardView.setSpecialKeyListener(
            new ForkyzKeyboard.SpecialKeyListener() {
                @Override
                public void onKeyDown(@ForkyzKeyboard.SpecialKey int key) {
                    // ignore
                }

                @Override
                public void onKeyUp(@ForkyzKeyboard.SpecialKey int key) {
                    // ignore
                    switch (key) {
                    case ForkyzKeyboard.KEY_CHANGE_CLUE_DIRECTION:
                        getBoard().toggleDirection();
                        return;
                    case ForkyzKeyboard.KEY_NEXT_CLUE:
                        getBoard().nextWord();
                        return;
                    case ForkyzKeyboard.KEY_PREVIOUS_CLUE:
                        getBoard().previousWord();
                        return;
                    default:
                        // ignore
                    }
                }
            }
        );

        this.renderer = new PlayboardRenderer(
            board,
            metrics.densityDpi,
            metrics.widthPixels,
            !prefs.getBoolean("supressHints", false),
            this
        );

        float scale = prefs.getFloat(SCALE, 1.0F);

        if (scale > renderer.getDeviceMaxScale()) {
            scale = renderer.getDeviceMaxScale();
        } else if (scale < renderer.getDeviceMinScale()) {
            scale = renderer.getDeviceMinScale();
        } else if (Float.isNaN(scale)) {
            scale = 1F;
        }
        prefs.edit().putFloat(SCALE, scale).apply();

        renderer.setScale(scale);
        board.setSkipCompletedLetters(
            this.prefs.getBoolean("skipFilled", false)
        );

        if(this.clue != null) {
            this.clue.setClickable(true);
            this.clue.setOnClickListener(new OnClickListener() {
                public void onClick(View arg0) {
                    if (PlayActivity.this.prefs.getBoolean(SHOW_CLUES_TAB, true)) {
                        PlayActivity.this.hideClueTabs();
                        PlayActivity.this.render(true);
                    } else {
                        PlayActivity.this.showClueTabs();
                        PlayActivity.this.render(true);
                    }
                }
            });
            this.clue.setOnLongClickListener(new OnLongClickListener() {
                public boolean onLongClick(View arg0) {
                    PlayActivity.this.launchClueList();
                    return true;
                }
            });
        }

        this.boardView.setCurrentScale(scale);
        this.boardView.setFocusable(true);
        this.registerForContextMenu(boardView);
        boardView.setContextMenuListener(new ClickListener() {
            public void onContextMenu(final Point e) {
                handler.post(() -> {
                    try {
                        Position p = renderer.findBox(e);
                        Word w = getBoard().setHighlightLetter(p);
                        boolean displayScratch = prefs.getBoolean("displayScratch", false);
                        renderer.draw(w, displayScratch, displayScratch);

                        launchClueNotes();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                });
            }

            public void onTap(Point e) {
                try {
                    if (prefs.getBoolean("doubleTap", false)
                            && ((System.currentTimeMillis() - lastTap) < 300)) {
                        fitToScreen();
                    } else {
                        Position p = renderer.findBox(e);
                        if (getBoard().isInWord(p)) {
                            Word previous = getBoard().setHighlightLetter(p);
                            displayKeyboard(previous);
                        }
                    }

                    lastTap = System.currentTimeMillis();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
        // constrain to 1:1 if clueTabs is showing
        boardView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            public void onLayoutChange(View v,
              int left, int top, int right, int bottom,
              int leftWas, int topWas, int rightWas, int bottomWas
            ) {
                boolean constrainedDims = false;

                ConstraintSet set = new ConstraintSet();
                set.clone(constraintLayout);

                boolean showCluesTab = PlayActivity.this.prefs.getBoolean(
                    SHOW_CLUES_TAB, true
                );

                if (showCluesTab) {
                    int height = bottom - top;
                    int width = right - left;

                    int orientation
                        = PlayActivity.this
                            .getResources()
                            .getConfiguration()
                            .orientation;

                    boolean portrait
                        = orientation == Configuration.ORIENTATION_PORTRAIT;

                    if (portrait && height > width) {
                        constrainedDims = true;
                        set.constrainMaxHeight(
                            boardView.getId(),
                            (int)(BOARD_DIM_RATIO * width)
                        );
                    }
                } else {
                    set.constrainMaxHeight(boardView.getId(), 0);
                }

                set.applyTo(constraintLayout);

                // if the view changed size, then rescale the view
                // cannot change layout during a layout change, so
                // use a predraw listener that requests a new layout
                // (via render) and returns false to cancel the
                // current draw
                if (constrainedDims ||
                    left != leftWas || right != rightWas ||
                    top != topWas || bottom != bottomWas) {
                    boardView.getViewTreeObserver()
                             .addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                        public boolean onPreDraw() {
                            PlayActivity.this.render(true);
                            PlayActivity.this
                                        .boardView
                                        .getViewTreeObserver()
                                        .removeOnPreDrawListener(this);
                            return false;
                        }
                    });
                }
            }
        });

        this.boardView.setFocusable(true);
        this.boardView.setScaleListener(new ScaleListener() {
            public void onScale(float newScale, final Point center) {
                int w = boardView.getImageView().getWidth();
                int h = boardView.getImageView().getHeight();
                float scale = renderer.fitTo((w < h) ? w : h);
                prefs.edit().putFloat(SCALE, scale).apply();
                lastTap = System.currentTimeMillis();
            }
        });

        int smallClueTextSize
            = getResources().getInteger(R.integer.small_clue_text_size);
        this.setClueSize(prefs.getInt("clueSize", smallClueTextSize));
        if (this.prefs.getBoolean("fitToScreen", false) || (ForkyzApplication.isLandscape(metrics)) && (ForkyzApplication.isTabletish(metrics) || ForkyzApplication.isMiniTabletish(metrics))) {
            this.handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    boardView.scrollTo(0, 0);

                    int v = (boardView.getWidth() < boardView.getHeight()) ? boardView
                            .getWidth() : boardView.getHeight();
                    if (v == 0) {
                        handler.postDelayed(this, 100);
                    }
                    float newScale = renderer.fitTo(v);
                    boardView.setCurrentScale(newScale);

                    prefs.edit().putFloat(SCALE, newScale).apply();
                    render();
                }
            }, 100);

        }

        registerBoard();
    }

    private static String neverNull(String val) {
        return val == null ? "" : val.trim();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.play_menu, menu);
        return true;
    }

    public boolean onPrepareOptionsMenu(Menu menu) {
        Puzzle puz = getPuzzle();

        if (renderer == null
                || renderer.getScale() >= renderer.getDeviceMaxScale()) {
            menu.removeItem(R.id.play_menu_zoom_in_max);
        }

        if (puz == null || puz.isUpdatable()) {
            menu.findItem(R.id.play_menu_reveal).setEnabled(false);
        } else {
            if (ForkyzApplication.isTabletish(metrics)) {
                menu.findItem(R.id.play_menu_reveal).setShowAsAction(MenuItem.SHOW_AS_ACTION_WITH_TEXT);
            }
        }

        if (puz == null || puz.getSupportUrl() == null) {
            MenuItem support = menu.findItem(R.id.play_menu_support_source);
            support.setVisible(false);
            support.setEnabled(false);
        }

        menu.findItem(R.id.play_menu_scratch_mode).setChecked(isScratchMode());

        menu.findItem(R.id.play_menu_show_errors).setEnabled(
            !(puz == null || puz.isUpdatable())
        );

        boolean showErrorsGrid
            = this.prefs.getBoolean(PREF_SHOW_ERRORS_GRID, false);
        boolean showErrorsCursor
            = this.prefs.getBoolean(PREF_SHOW_ERRORS_CURSOR, false);

        int showErrorsTitle = (showErrorsGrid || showErrorsCursor)
            ? R.string.showing_errors
            : R.string.show_errors;

        menu.findItem(R.id.play_menu_show_errors)
            .setTitle(showErrorsTitle);

        menu.findItem(R.id.play_menu_show_errors_grid)
            .setChecked(showErrorsGrid);
        menu.findItem(R.id.play_menu_show_errors_cursor)
            .setChecked(showErrorsCursor);

        return true;
    }

    private SpannableString createSpannableForMenu(String value){
        SpannableString s = new SpannableString(value);
        s.setSpan(new ForegroundColorSpan(ContextCompat.getColor(this, R.color.textColorPrimary)), 0, s.length(), 0);
        return s;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
        case KeyEvent.KEYCODE_BACK:
        case KeyEvent.KEYCODE_ESCAPE:
        case KeyEvent.KEYCODE_SEARCH:
        case KeyEvent.KEYCODE_DPAD_UP:
        case KeyEvent.KEYCODE_DPAD_DOWN:
        case KeyEvent.KEYCODE_DPAD_LEFT:
        case KeyEvent.KEYCODE_DPAD_RIGHT:
        case KeyEvent.KEYCODE_DPAD_CENTER:
        case KeyEvent.KEYCODE_SPACE:
        case KeyEvent.KEYCODE_ENTER:
        case KeyEvent.KEYCODE_DEL:
            return true;
        }

        char c = Character.toUpperCase(event.getDisplayLabel());
        if (PlayActivity.ALPHA.indexOf(c) != -1)
            return true;

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {

        boolean handled = false;

        // handle back separately as it we shouldn't block a keyboard
        // hide because of it
        if (keyCode == KeyEvent.KEYCODE_BACK
                || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            if (!keyboardManager.handleBackKey()) {
                this.finish();
            }
            handled = true;
        }

        keyboardManager.pushBlockHide();

        if (getBoard() != null) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_SEARCH:
                    getBoard().nextWord();
                    handled = true;
                    break;

                case KeyEvent.KEYCODE_DPAD_DOWN:
                    getBoard().moveDown();
                    handled = true;
                    break;

                case KeyEvent.KEYCODE_DPAD_UP:
                    getBoard().moveUp();
                    handled = true;
                    break;

                case KeyEvent.KEYCODE_DPAD_LEFT:
                    getBoard().moveLeft();
                    handled = true;
                    break;

                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    getBoard().moveRight();
                    handled = true;
                    break;

                case KeyEvent.KEYCODE_DPAD_CENTER:
                    getBoard().toggleDirection();
                    handled = true;
                    break;

                case KeyEvent.KEYCODE_SPACE:
                    if (prefs.getBoolean("spaceChangesDirection", true)) {
                        getBoard().toggleDirection();
                    } else if (isScratchMode()) {
                        getBoard().playScratchLetter(' ');
                    } else {
                        getBoard().playLetter(' ');
                    }
                    handled = true;
                    break;

                case KeyEvent.KEYCODE_ENTER:
                    if (prefs.getBoolean("enterChangesDirection", true)) {
                        getBoard().toggleDirection();
                    } else {
                        getBoard().nextWord();
                    }
                    handled = true;
                    break;

                case KeyEvent.KEYCODE_DEL:
                    if (isScratchMode()) {
                        getBoard().deleteScratchLetter();
                    } else {
                        getBoard().deleteLetter();
                    }
                    handled = true;
                    break;
            }

            char c = Character.toUpperCase(event.getDisplayLabel());

            if (!handled && ALPHA.indexOf(c) != -1) {
                if (isScratchMode()) {
                    getBoard().playScratchLetter(c);
                } else {
                    getBoard().playLetter(c);
                }
                handled = true;
            }
        }

        if (!handled)
            handled = super.onKeyUp(keyCode, event);

        keyboardManager.popBlockHide();

        return handled;
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        return onOptionsItemSelected(item);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            finish();
            return true;
        }

        if (getBoard() != null) {
            if (id == R.id.play_menu_reveal_letter) {
                getBoard().revealLetter();
                return true;
            } else if (id == R.id.play_menu_reveal_word) {
                getBoard().revealWord();
                return true;
            } else if (id == R.id.play_menu_reveal_errors) {
                getBoard().revealErrors();
                return true;
            } else if (id == R.id.play_menu_reveal_puzzle) {
                showRevealPuzzleDialog();
                return true;
            } else if (id == R.id.play_menu_show_errors_grid) {
                getBoard().toggleShowErrorsGrid();
                this.prefs.edit().putBoolean(
                    PREF_SHOW_ERRORS_GRID, getBoard().isShowErrorsGrid()
                ).apply();
                invalidateOptionsMenu();
                return true;
            } else if (id == R.id.play_menu_show_errors_cursor) {
                getBoard().toggleShowErrorsCursor();
                this.prefs.edit().putBoolean(
                    PREF_SHOW_ERRORS_CURSOR, getBoard().isShowErrorsCursor()
                ).apply();
                invalidateOptionsMenu();
                return true;
            } else if (id == R.id.play_menu_scratch_mode) {
                toggleScratchMode();
                return true;
            } else if (id == R.id.play_menu_settings) {
                Intent i = new Intent(this, PreferencesActivity.class);
                this.startActivity(i);
                return true;
            } else if (id == R.id.play_menu_zoom_in) {
                float newScale = renderer.zoomIn();
                this.prefs.edit().putFloat(SCALE, newScale).apply();
                boardView.setCurrentScale(newScale);
                this.render(true);

                return true;
            } else if (id == R.id.play_menu_zoom_in_max) {
                float newScale = renderer.zoomInMax();
                this.prefs.edit().putFloat(SCALE, newScale).apply();
                boardView.setCurrentScale(newScale);
                this.render(true);

                return true;
            } else if (id == R.id.play_menu_zoom_out) {
                float newScale = renderer.zoomOut();
                this.prefs.edit().putFloat(SCALE, newScale).apply();
                boardView.setCurrentScale(newScale);
                this.render(true);

                return true;
            } else if (id == R.id.play_menu_zoom_fit) {
                fitToScreen();
                return true;
            } else if (id == R.id.play_menu_zoom_reset) {
                float newScale = renderer.zoomReset();
                boardView.setCurrentScale(newScale);
                this.prefs.edit().putFloat(SCALE, newScale).apply();
                this.render(true);

                return true;
            } else if (id == R.id.play_menu_info) {
                showInfoDialog();
                return true;
            } else if (id == R.id.play_menu_clues) {
                PlayActivity.this.launchClueList();
                return true;
            } else if (id == R.id.play_menu_clue_notes) {
                launchClueNotes();
                return true;
            } else if (id == R.id.play_menu_player_notes) {
                launchPuzzleNotes();
                return true;
            } else if (id == R.id.play_menu_help) {
                Intent helpIntent = new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("file:///android_asset/playscreen.html"),
                    this,
                    HTMLActivity.class
                );
                this.startActivity(helpIntent);
                return true;
            } else if (id == R.id.play_menu_clue_size_s) {
                this.setClueSize(
                    getResources().getInteger(R.integer.small_clue_text_size)
                );
                return true;
            } else if (id == R.id.play_menu_clue_size_m) {
                this.setClueSize(
                    getResources().getInteger(R.integer.medium_clue_text_size)
                );
                return true;
            } else if (id == R.id.play_menu_clue_size_l) {
                this.setClueSize(
                    getResources().getInteger(R.integer.large_clue_text_size)
                );
                return true;
            } else if (id == R.id.play_menu_support_source) {
                actionSupportSource();
                return true;
            }
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onClueTabsClick(Clue clue, ClueTabs view) {
        Playboard board = getBoard();
        if (board == null)
            return;

        if (!clue.isAcross() && !clue.isDown())
            return;

        Word old = board.getCurrentWord();
        if (clue.isAcross())
            board.jumpToClue(clue.getNumber(), true);
        else if (clue.isDown())
            board.jumpToClue(clue.getNumber(), true);
        displayKeyboard(old);
    }

    @Override
    public void onClueTabsLongClick(Clue clue, ClueTabs view) {
        Playboard board = getBoard();
        if (board == null)
            return;

        if (getPuzzle().isNotableClue(clue)) {
            if (clue.isAcross() && clue.hasNumber())
                board.jumpToClue(clue.getNumber(), true);
            else if (clue.isDown() && clue.hasNumber())
                board.jumpToClue(clue.getNumber(), false);
            launchClueNotes();
        } else {
            launchPuzzleNotes();
        }
    }

    @Override
    public void onClueTabsBarSwipeDown(ClueTabs view) {
        hideClueTabs();
        render(true);
    }

    @Override
    public void onClueTabsBarLongclick(ClueTabs view) {
        hideClueTabs();
        render(true);
    }

    @Override
    public void onClueTabsPageChange(ClueTabs view, int pageNumber) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(CLUE_TABS_PAGE, pageNumber);
        editor.apply();
    }

    public void onPlayboardChange(
        boolean wholeBoard, Word currentWord, Word previousWord
    ) {
        super.onPlayboardChange(wholeBoard, currentWord, previousWord);

        // hide keyboard when moving to a new word
        Position newPos = getBoard().getHighlightLetter();
        if ((previousWord == null) ||
            !previousWord.checkInWord(newPos.getRow(), newPos.getCol())) {
            keyboardManager.hideKeyboard();
        }

        if (!wholeBoard)
            render(previousWord, false);
        else
            render(false);
    }

    private void fitToScreen() {
        this.boardView.scrollTo(0, 0);

        int v = (this.boardView.getWidth() < this.boardView.getHeight()) ? this.boardView
                .getWidth() : this.boardView.getHeight();
        float newScale = renderer.fitTo(v);
        this.prefs.edit().putFloat(SCALE, newScale).apply();
        boardView.setCurrentScale(newScale);
        this.render(true);
    }

    @Override
    protected void onTimerUpdate() {
        super.onTimerUpdate();

        Puzzle puz = getPuzzle();
        ImaginaryTimer timer = getTimer();

        if (puz != null && timer != null) {
            getWindow().setTitle(timer.time());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        keyboardManager.onPause();

        Playboard board = getBoard();
        if (board != null)
            board.removeListener(this);

        if (clueTabs != null) {
            clueTabs.removeListener(this);
            clueTabs.unlistenBoard();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        this.onConfigurationChanged(getBaseContext().getResources()
                                                    .getConfiguration());

        if (keyboardManager != null)
            keyboardManager.onResume();

        if (prefs.getBoolean(SHOW_CLUES_TAB, false)) {
            showClueTabs();
        } else {
            hideClueTabs();
        }

        registerBoard();
    }

    private void registerBoard() {
        Playboard board = getBoard();
        Puzzle puz = getPuzzle();

        if (board == null || puz == null) {
            LOG.info("PlayActivity resumed but no Puzzle selected, finishing.");
            finish();
            return;
        }

        setTitle(getString(
            R.string.play_activity_title,
            neverNull(puz.getTitle()),
            neverNull(puz.getAuthor()),
            neverNull(puz.getCopyright())
        ));

        boolean showErrorsGrid
            = this.prefs.getBoolean(PREF_SHOW_ERRORS_GRID, false);
        if (board.isShowErrorsGrid() != showErrorsGrid) {
            board.toggleShowErrorsGrid();
        }

        boolean showErrorsCursor
            = this.prefs.getBoolean(PREF_SHOW_ERRORS_CURSOR, false);
        if (board.isShowErrorsCursor() != showErrorsCursor) {
            board.toggleShowErrorsCursor();
        }

        if (clueTabs != null) {
            clueTabs.setBoard(board);
            clueTabs.setPage(prefs.getInt(CLUE_TABS_PAGE, 0));
            clueTabs.addListener(this);
            clueTabs.listenBoard();
            clueTabs.refresh();
        }

        if (board != null) {
            board.setSkipCompletedLetters(this.prefs.getBoolean("skipFilled", false));
            board.setMovementStrategy(this.getMovementStrategy());
            board.addListener(this);

            keyboardManager.attachKeyboardToView(boardView);

            render(true);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (keyboardManager != null)
            keyboardManager.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (keyboardManager != null)
            keyboardManager.onDestroy();
    }

    private void setClueSize(int dps) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            this.clue.setAutoSizeTextTypeUniformWithConfiguration(
                5, dps, 1, TypedValue.COMPLEX_UNIT_SP
            );
        }

        int smallClueTextSize
            = getResources().getInteger(R.integer.small_clue_text_size);
        if (prefs.getInt("clueSize", smallClueTextSize) != dps) {
            this.prefs.edit().putInt("clueSize", dps).apply();
        }
    }

    protected MovementStrategy getMovementStrategy() {
        if (movement != null) {
            return movement;
        } else {
            return ForkyzApplication.getInstance().getMovementStrategy();
        }
    }

    /**
     * Change keyboard display if the same word has been selected twice
     */
    private void displayKeyboard(Word previous) {
        // only show keyboard if double click a word
        // hide if it's a new word
        Playboard board = getBoard();
        if (board != null) {
            Position newPos = board.getHighlightLetter();
            if ((previous != null) &&
                previous.checkInWord(newPos.getRow(), newPos.getCol())) {
                keyboardManager.showKeyboard(boardView);
            } else {
                keyboardManager.hideKeyboard();
            }
        }
    }

    private void render() {
        render(null);
    }

    private void render(boolean rescale) {
        this.render(null, rescale);
    }

    private void render(Word previous) {
        this.render(previous, false);
    }

    private void render(Word previous, boolean rescale) {
        if (getBoard() == null)
            return;

        boolean displayScratch = this.prefs.getBoolean("displayScratch", false);
        this.boardView.setBitmap(
            renderer.draw(previous, displayScratch, displayScratch),
            rescale
        );
        this.boardView.setContentDescription(
            renderer.getContentDescription(this.boardViewDescriptionBase)
        );
        this.boardView.requestFocus();
        /*
         * If we jumped to a new word, ensure the first letter is visible.
         * Otherwise, insure that the current letter is visible. Only necessary
         * if the cursor is currently off screen.
         */
        if (this.prefs.getBoolean("ensureVisible", true)) {
            Playboard board = getBoard();
            Word currentWord = board.getCurrentWord();
            Position cursorPos = board.getHighlightLetter();

            Point topLeft;
            Point bottomRight;
            Point cursorTopLeft;
            Point cursorBottomRight;

            cursorTopLeft = renderer.findPointTopLeft(cursorPos);
            cursorBottomRight = renderer.findPointBottomRight(cursorPos);

            if ((previous != null) && previous.equals(currentWord)) {
                topLeft = cursorTopLeft;
                bottomRight = cursorBottomRight;
            } else {
                topLeft = renderer.findPointTopLeft(currentWord);
                bottomRight = renderer.findPointBottomRight(currentWord);
            }

            this.boardView.ensureVisible(bottomRight);
            this.boardView.ensureVisible(topLeft);

            // ensure the cursor is always on the screen.
            this.boardView.ensureVisible(cursorBottomRight);
            this.boardView.ensureVisible(cursorTopLeft);
        }

        Clue c = getBoard().getClue();
        this.clue.setText(smartHtml(
            getLongClueText(c, getBoard().getCurrentWord().length)
        ));

        this.boardView.requestFocus();
    }

    private void launchClueNotes() {
        Intent i = new Intent(this, NotesActivity.class);
        i.putExtra(NotesActivity.PUZZLE_NOTES, false);
        this.startActivity(i);
    }

    private void launchPuzzleNotes() {
        Intent i = new Intent(this, NotesActivity.class);
        i.putExtra(NotesActivity.PUZZLE_NOTES, true);
        this.startActivity(i);
    }

    private void launchClueList() {
        Intent i = new Intent(this, ClueListActivity.class);
        PlayActivity.this.startActivity(i);
    }

    /**
     * Changes the constraints on clue tabs to show.
     *
     * Call render(true) after to rescale board. Updates shared prefs.
     */
    private void showClueTabs() {
        ConstraintSet set = new ConstraintSet();
        set.clone(constraintLayout);
        set.setVisibility(clueTabs.getId(), ConstraintSet.VISIBLE);
        set.applyTo(constraintLayout);

        clueTabs.setPage(prefs.getInt(CLUE_TABS_PAGE, 0));

        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(SHOW_CLUES_TAB, true);
        editor.apply();
    }

    /**
     * Changes the constraints on clue tabs to hide.
     *
     * Call render(true) after to rescale board. Updates shared prefs.
     */
    private void hideClueTabs() {
        ConstraintSet set = new ConstraintSet();
        set.clone(constraintLayout);
        set.setVisibility(clueTabs.getId(), ConstraintSet.GONE);
        set.applyTo(constraintLayout);

        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(SHOW_CLUES_TAB, false);
        editor.apply();
    }

    private void showInfoDialog() {
        DialogFragment dialog = new InfoDialog();
        dialog.show(getSupportFragmentManager(), "InfoDialog");
    }

    private void showRevealPuzzleDialog() {
        DialogFragment dialog = new RevealPuzzleDialog();
        dialog.show(getSupportFragmentManager(), "RevealPuzzleDialog");
    }

    public static class InfoDialog extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder
                = new AlertDialog.Builder(getActivity());
            LayoutInflater inflater = requireActivity().getLayoutInflater();

            View view = inflater.inflate(R.layout.puzzle_info_dialog, null);

            PlayActivity activity = (PlayActivity) getActivity();

            Puzzle puz = activity.getPuzzle();
            if (puz != null) {
                TextView title = view.findViewById(R.id.puzzle_info_title);
                title.setText(smartHtml(puz.getTitle()));

                TextView author = view.findViewById(R.id.puzzle_info_author);
                author.setText(puz.getAuthor());

                TextView copyright
                    = view.findViewById(R.id.puzzle_info_copyright);
                copyright.setText(smartHtml(puz.getCopyright()));

                TextView time = view.findViewById(R.id.puzzle_info_time);

                ImaginaryTimer timer = activity.getTimer();
                if (timer != null) {
                    timer.stop();
                    time.setText(getString(
                        R.string.elapsed_time, timer.time()
                    ));
                    timer.start();
                } else {
                    time.setText(getString(
                        R.string.elapsed_time,
                        new ImaginaryTimer(puz.getTime()).time()
                    ));
                }

                ProgressBar progress = (ProgressBar) view
                        .findViewById(R.id.puzzle_info_progress);
                progress.setProgress(puz.getPercentComplete());

                TextView filename
                    = view.findViewById(R.id.puzzle_info_filename);
                FileHandler fileHandler
                    = ForkyzApplication.getInstance().getFileHandler();
                filename.setText(
                    fileHandler.getUri(activity.getPuzHandle()).toString()
                );

                addNotes(view);
            }

            builder.setView(view);

            return builder.create();
        }

        private void addNotes(View dialogView) {
            TextView view = dialogView.findViewById(R.id.puzzle_info_notes);

            Puzzle puz = ((PlayActivity) getActivity()).getPuzzle();
            if (puz == null)
                return;

            String puzNotes = puz.getNotes();
            if (puzNotes == null)
                puzNotes = "";

            final String notes = puzNotes;

            String[] split =
                notes.split("(?i:(?m:^\\s*Across:?\\s*$|^\\s*\\d))", 2);

            final String text = split[0].trim();

            if (text.length() > 0) {
                view.setText(smartHtml(
                    getString(R.string.tap_to_show_full_notes_with_text, text)
                ));
            } else {
                view.setText(getString(
                    R.string.tap_to_show_full_notes_no_text
                ));
            }

            view.setOnClickListener(new OnClickListener() {
                private boolean showAll = true;

                public void onClick(View view) {
                    TextView tv = (TextView) view;

                    if (showAll) {
                        if (notes == null || notes.length() == 0) {
                            tv.setText(getString(
                                R.string.tap_to_hide_full_notes_no_text
                            ));
                        } else {
                            tv.setText(smartHtml(
                                getString(
                                    R.string.tap_to_hide_full_notes_with_text,
                                    notes
                                )
                            ));
                        }
                    } else {
                        if (text == null || text.length() == 0) {
                            tv.setText(getString(
                                R.string.tap_to_show_full_notes_no_text
                            ));
                        } else {
                            tv.setText(smartHtml(
                                getString(
                                    R.string.tap_to_show_full_notes_with_text,
                                    text
                                )
                            ));
                        }
                    }

                    showAll = !showAll;
                }
            });
        }
    }

    public static class RevealPuzzleDialog extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder
                = new AlertDialog.Builder(getActivity());

            builder.setTitle(getString(R.string.reveal_puzzle))
                .setMessage(getString(R.string.are_you_sure))
                .setPositiveButton(
                    R.string.ok,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            Playboard board
                                = ((PlayActivity) getActivity()).getBoard();
                            if (board != null)
                                 board.revealPuzzle();
                        }
                    }
                )
                .setNegativeButton(
                    R.string.cancel,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    }
                );

            return builder.create();
        }
    }

    @SuppressWarnings("deprecation")
    private void setFullScreenMode() {
        if (prefs.getBoolean("fullScreen", false)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                final WindowInsetsController insetsController
                    = getWindow().getInsetsController();
                if (insetsController != null) {
                    insetsController.hide(WindowInsets.Type.statusBars());
                }
            } else {
                getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                        WindowManager.LayoutParams.FLAG_FULLSCREEN);
            }
        }
    }

    private void actionSupportSource() {
        Puzzle puz = getPuzzle();
        if (puz != null) {
            String supportUrl = puz.getSupportUrl();
            if (supportUrl != null) {
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(supportUrl));
                startActivity(i);
            }
        }
    }

    private boolean isScratchMode() {
        return this.prefs.getBoolean("scratchMode", false);
    }

    private void toggleScratchMode() {
        boolean scratchMode = isScratchMode();
        this.prefs.edit().putBoolean(
            "scratchMode", !scratchMode
        ).apply();
        invalidateOptionsMenu();
    }
}
