package app.crossword.yourealwaysbe.view;

import app.crossword.yourealwaysbe.forkyz.R;
import app.crossword.yourealwaysbe.puz.Clue;
import app.crossword.yourealwaysbe.puz.ClueList;
import app.crossword.yourealwaysbe.puz.Playboard.Word;
import app.crossword.yourealwaysbe.puz.Playboard;
import app.crossword.yourealwaysbe.puz.Puzzle.ClueNumDir;
import app.crossword.yourealwaysbe.puz.Puzzle;
import app.crossword.yourealwaysbe.util.WeakSet;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AttributeSet;
import android.view.GestureDetector.OnGestureListener;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckedTextView;
import android.widget.LinearLayout;
import androidx.core.content.ContextCompat;
import androidx.core.view.GestureDetectorCompat;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public class ClueTabs extends LinearLayout
                      implements Playboard.PlayboardListener {
    private static final Logger LOG = Logger.getLogger("app.crossword.yourealwaysbe");

    private static final int ACROSS_PAGE_INDEX = 0;
    private static final int DOWN_PAGE_INDEX = 1;
    private static final int HISTORY_PAGE_INDEX = 2;

    public static enum PageType {
        ACROSS, DOWN, HISTORY;
    }

    private ViewPager2 viewPager;
    private Playboard board;
    private boolean listening = false;
    private Set<ClueTabsListener> listeners = WeakSet.buildSet();
    private boolean forceSnap = false;

    public static interface ClueTabsListener {
        /**
         * When the user clicks a clue
         *
         * @param clue the clue clicked
         * @param view the view calling
         */
        default void onClueTabsClick(Clue clue,
                                     ClueTabs view) { }

        /**
         * When the user long-presses a clue
         *
         * @param clue the clue clicked
         * @param view the view calling
         */
        default void onClueTabsLongClick(Clue clue,
                                         ClueTabs view) { }

        /**
         * When the user swipes up on the tab bar
         *
         * @param view the view calling
         */
        default void onClueTabsBarSwipeUp(ClueTabs view) { }

        /**
         * When the user swipes down on the tab bar
         *
         * @param view the view calling
         */
        default void onClueTabsBarSwipeDown(ClueTabs view) { }

        /**
         * When the user swipes down on the tab bar
         *
         * @param view the view calling
         */
        default void onClueTabsBarLongclick(ClueTabs view) { }

        /**
         * When the user changes the page being viewed
         *
         * @param view the view calling
         */
        default void onClueTabsPageChange(ClueTabs view, int pageNumber) { }
    }

    public ClueTabs(Context context, AttributeSet as) {
        super(context, as);
        LayoutInflater.from(context).inflate(R.layout.clue_tabs, this);
    }

    /**
     * Does nothing if the same board is already set
     */
    public void setBoard(Playboard board) {
        if (board == null)
            return;

        // same board, nothing to do (avoid rebuilding adapters and
        // losing position)
        if (board == this.board)
            return;

        // ignore old board if there was one
        unlistenBoard();

        this.board = board;

        TabLayout tabLayout = findViewById(R.id.clueTabsTabLayout);
        viewPager = findViewById(R.id.clueTabsPager);

        viewPager.setAdapter(new ClueTabsPagerAdapter());

        viewPager.registerOnPageChangeCallback(
            new ViewPager2.OnPageChangeCallback() {
                public void onPageSelected(int position) {
                    ClueTabs.this.notifyListenersPageChanged(position);
                }
            }
        );

        Context context = getContext();

        new TabLayoutMediator(tabLayout, viewPager,
            (tab, position) -> {
                switch (position) {
                case ACROSS_PAGE_INDEX:
                    tab.setText(context.getString(R.string.clue_tab_across));
                    break;
                case DOWN_PAGE_INDEX:
                    tab.setText(context.getString(R.string.clue_tab_down));
                    break;
                case HISTORY_PAGE_INDEX:
                    tab.setText(context.getString(R.string.clue_tab_history));
                    break;
                }
            }
        ).attach();

        setTabLayoutOnTouchListener();

        LinearLayout tabStrip = (LinearLayout) tabLayout.getChildAt(0);
        for (int i = 0; i < tabStrip.getChildCount(); i++) {
            tabStrip.getChildAt(i).setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    ClueTabs.this.notifyListenersTabsBarLongClick();
                    return true;
                }
            });
        }
    }

    public void setPage(int pageNumber) {
        if (viewPager != null) {
            viewPager.setCurrentItem(pageNumber, false);
        }
    }

    public PageType getCurrentPageType() {
        if (viewPager != null) {
            int curPage = viewPager.getCurrentItem();
            ClueTabsPagerAdapter adapter
                = (ClueTabsPagerAdapter) viewPager.getAdapter();
            return adapter.getPageType(curPage);
        }
        return null;
    }

    public void nextPage() {
        if (viewPager != null) {
            int curPage = viewPager.getCurrentItem();
            int numPages = viewPager.getAdapter().getItemCount();
            viewPager.setCurrentItem((curPage + 1) % numPages, false);
        }
    }

    public void prevPage() {
        if (viewPager != null) {
            int curPage = viewPager.getCurrentItem();
            int numPages = viewPager.getAdapter().getItemCount();
            int nextPage = curPage == 0 ? numPages - 1 : curPage - 1;
            viewPager.setCurrentItem(nextPage, false);
        }
    }

    /**
     * Always snap to clue if true, else follow prefs
     */
    public void setForceSnap(boolean forceSnap) {
        this.forceSnap = forceSnap;
    }

    /**
     * Refresh view to match current board state
     */
    public void refresh() {
        // make sure up to date with board
        if (viewPager != null)
            viewPager.getAdapter().notifyDataSetChanged();
    }

    public void addListener(ClueTabsListener listener) {
        listeners.add(listener);
    }

    public void removeListener(ClueTabsListener listener) {
        listeners.remove(listener);
    }

    public void listenBoard() {
        if (board != null && !listening) {
            board.addListener(this);
            listening = true;
        }
    }

    public void unlistenBoard() {
        if (board != null && listening) {
            board.removeListener(this);
            listening = false;
        }
    }

    public void onResume() {

    }

    public void onPause() {

    }

    public void onPlayboardChange(
        boolean wholeBoard, Word currentWord, Word previousWord
    ) {
        if (viewPager != null) {
            if (isSnapToClue()) {
                if (board.isAcross())
                    viewPager.setCurrentItem(ACROSS_PAGE_INDEX);
                else
                    viewPager.setCurrentItem(DOWN_PAGE_INDEX);
            }
        }
    }

    private boolean isSnapToClue() {
        SharedPreferences prefs
            = PreferenceManager.getDefaultSharedPreferences(
                getContext()
            );

        return forceSnap || prefs.getBoolean("snapClue", false);
    }

    private void notifyListenersClueClick(Clue clue) {
        for (ClueTabsListener listener : listeners)
            listener.onClueTabsClick(clue, this);
    }

    private void notifyListenersClueLongClick(Clue clue) {
        for (ClueTabsListener listener : listeners)
            listener.onClueTabsLongClick(clue, this);
    }

    private void notifyListenersTabsBarSwipeUp() {
        for (ClueTabsListener listener : listeners)
            listener.onClueTabsBarSwipeUp(this);
    }

    private void notifyListenersTabsBarSwipeDown() {
        for (ClueTabsListener listener : listeners)
            listener.onClueTabsBarSwipeDown(this);
    }

    private void notifyListenersTabsBarLongClick() {
        for (ClueTabsListener listener : listeners)
            listener.onClueTabsBarSwipeDown(this);
    }

    private void notifyListenersPageChanged(int pageNumber) {
        for (ClueTabsListener listener : listeners)
            listener.onClueTabsPageChange(this, pageNumber);
    }

    private class ClueTabsPagerAdapter extends RecyclerView.Adapter<ClueListHolder> {
        @Override
        public ClueListHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View page = LayoutInflater.from(parent.getContext())
                                      .inflate(R.layout.clue_tabs_page,
                                               parent,
                                               false);
            return new ClueListHolder(page);
        }

        @Override
        public void onBindViewHolder(ClueListHolder holder, int position) {
            holder.setContents(getPageType(position));
        }

        public PageType getPageType(int position) {
            switch (position) {
            case ACROSS_PAGE_INDEX:
                return PageType.ACROSS;
            case DOWN_PAGE_INDEX:
                return PageType.DOWN;
            case HISTORY_PAGE_INDEX:
                return PageType.HISTORY;
            default:
                return null;
            }
        }

        @Override
        public int getItemCount() {
            return 3;
        }
    }

    private class ClueListHolder
            extends RecyclerView.ViewHolder
            implements Playboard.PlayboardListener {

        private RecyclerView clueList;
        private ClueListAdapter clueListAdapter;
        private LinearLayoutManager layoutManager;
        private PageType pageType;

        public ClueListHolder(View view) {
            super(view);
            Context context = itemView.getContext();
            clueList = view.findViewById(R.id.tabClueList);

            layoutManager = new LinearLayoutManager(context);
            clueList.setLayoutManager(layoutManager);
            clueList.setItemAnimator(new DefaultItemAnimator());
            clueList.addItemDecoration(
                new DividerItemDecoration(context,
                                          DividerItemDecoration.VERTICAL)
            );

            ClueTabs.this.board.addListener(this);
        }


        public void onPlayboardChange(
            boolean wholeBoard, Word currentWord, Word previousWord
        ) {
            if (clueListAdapter == null)
                return;

            if (wholeBoard) {
                clueListAdapter.notifyDataSetChanged();
                return;
            }

            if (previousWord == null) {
                clueListAdapter.notifyClueSelectionChange(
                    currentWord.across, currentWord.number
                );
            } else {
                clueListAdapter.notifyClueSelectionChange(
                    currentWord.across, currentWord.number,
                    previousWord.across, previousWord.number
                );
            }
        }

        public void setContents(PageType pageType) {

            Playboard board = ClueTabs.this.board;
            Puzzle puz = board.getPuzzle();

            if (board != null && this.pageType != pageType) {
                switch (pageType) {
                case ACROSS:
                case DOWN:
                    boolean across = pageType == PageType.ACROSS;
                    ClueList clues = puz.getClues(across);

                    clueListAdapter = new AcrossDownAdapter(clues, across);
                    clueList.setAdapter(clueListAdapter);
                    break;

                case HISTORY:
                    if (puz != null) {
                        clueListAdapter = new HistoryListAdapter(puz.getHistory());
                    } else {
                        clueListAdapter
                            = new HistoryListAdapter(new LinkedList<>());
                    }
                    clueList.setAdapter(clueListAdapter);
                    break;
                }

                this.pageType = pageType;
            }

            clueListAdapter.notifyDataSetChanged();

            if (board != null) {
                if (isSnapToClue()) {
                    switch (pageType) {
                    case ACROSS:
                    case DOWN:
                        int position = board.getCurrentClueIndex();
                        layoutManager.scrollToPositionWithOffset(position, 5);
                        break;
                    case HISTORY:
                        layoutManager.scrollToPositionWithOffset(0, 5);
                        break;
                    }
                }
            }
        }
    }

    private abstract class ClueListAdapter
            extends RecyclerView.Adapter<ClueViewHolder> {
        boolean showDirection;

        public ClueListAdapter(boolean showDirection) {
            this.showDirection = showDirection;
        }

        @Override
        public ClueViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View clueView = LayoutInflater.from(parent.getContext())
                                          .inflate(R.layout.clue_list_item,
                                                   parent,
                                                   false);
            return new ClueViewHolder(clueView, showDirection);
        }

        /**
         * Notify clue selection without previous word
         */
        public abstract void notifyClueSelectionChange(
            boolean curAcross, int curNumber
        );

        public abstract void notifyClueSelectionChange(
            boolean curAcross, int curNumber,
            boolean prevAcross, int prevNumber
        );
    }

    private class AcrossDownAdapter extends ClueListAdapter {
        private ClueList clueList;
        private List<Clue> rawClueList;
        private boolean across;

        public AcrossDownAdapter(ClueList clueList, boolean across) {
            super(false);
            this.clueList = clueList;
            this.across = across;
            this.rawClueList = new ArrayList<Clue>(clueList.getClues());
        }

        @Override
        public void onBindViewHolder(ClueViewHolder holder, int position) {
            Clue clue = rawClueList.get(position);
            holder.setClue(clue);
        }

        @Override
        public int getItemCount() {
            return clueList.size();
        }

        @Override
        public void notifyClueSelectionChange(
            boolean curAcross, int curNumber
        ) {
            if (curAcross == across) {
                int index = clueList.getClueIndex(curNumber);
                if (index > -1) {
                    notifyItemChanged(index);
                }
            }
        }

        @Override
        public void notifyClueSelectionChange(
            boolean curAcross, int curNumber,
            boolean prevAcross, int prevNumber
        ) {
            if (prevAcross == across) {
                int index = clueList.getClueIndex(prevNumber);
                if (index > -1) {
                    notifyItemChanged(index);
                }
            }
            notifyClueSelectionChange(curAcross, curNumber);
        }
    }

    public class HistoryListAdapter
           extends ClueListAdapter {

        private List<ClueNumDir> historyList;

        public HistoryListAdapter(List<ClueNumDir> historyList) {
            super(true);
            this.historyList = historyList;
        }

        @Override
        public void onBindViewHolder(ClueViewHolder holder, int position) {
            ClueNumDir item = historyList.get(position);
            Playboard board = ClueTabs.this.board;
            if (board != null) {
                int number = item.getClueNumber();
                boolean across = item.getAcross();
                Puzzle puz = board.getPuzzle();
                Clue clue = puz.getClues(across).getClue(number);
                if (puz != null && clue != null) {
                    holder.setClue(clue);
                }
            }
        }

        @Override
        public int getItemCount() {
            return historyList.size();
        }

        @Override
        public void notifyClueSelectionChange(
            boolean curAcross, int curNumber
        ) {
            // Can't know old index of current clue without maintaining
            // own copy of history list
            notifyDataSetChanged();
        }

        @Override
        public void notifyClueSelectionChange(
            boolean curAcross, int curNumber,
            boolean prevAcross, int prevNumber
        ) {
            notifyClueSelectionChange(curAcross, curNumber);
        }
    }

    private class ClueViewHolder extends RecyclerView.ViewHolder {
        private CheckedTextView clueView;
        private Clue clue;
        private boolean showDirection;

        public ClueViewHolder(View view, boolean showDirection) {
            super(view);
            this.clueView = view.findViewById(R.id.clue_text_view);
            this.showDirection = showDirection;

            this.clueView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    ClueTabs.this.notifyListenersClueClick(clue);
                }
            });

            this.clueView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {
                    ClueTabs.this.notifyListenersClueLongClick(clue);
                    return true;
                }
            });
        }

        /**
         * Set the clue in the holder
         *
         * @param clue the clue
         */
        public void setClue(Clue clue) {
            if (clue == null)
                return;

            Playboard board = ClueTabs.this.board;

            this.clue = clue;

            String clueText;
            if (showDirection) {
                int clueFormat = clue.getIsAcross()
                    ? R.string.clue_format_across_short
                    : R.string.clue_format_down_short;
                clueText = ClueTabs.this.getContext().getString(
                    clueFormat,
                    clue.getNumber(), clue.getHint()
                );
            } else {
                clueText = ClueTabs.this.getContext().getString(
                    R.string.clue_format_no_direction_short,
                    clue.getNumber(), clue.getHint()
                );
            }

            clueView.setText(clueText);

            int color = R.color.textColorPrimary;
            if (
                board != null
                    && board.isFilledClueNum(
                        clue.getNumber(), clue.getIsAcross()
                    )
            ) {
                color = R.color.textColorFilled;
            }

            clueView.setTextColor(ContextCompat.getColor(
                itemView.getContext(), color
            ));

            if (board != null) {
                int selectedClueNumber = board.getClueNumber();
                clueView.setChecked(
                    board.isAcross() == clue.getIsAcross()
                        && selectedClueNumber == clue.getNumber()
                );
            }
        }
    }

    // suppress because the swipe detector does not consume clicks
    @SuppressWarnings("ClickableViewAccessibility")
    private void setTabLayoutOnTouchListener() {
        TabLayout tabLayout = findViewById(R.id.clueTabsTabLayout);

        OnGestureListener tabSwipeListener
            = new SimpleOnGestureListener() {
                // as recommended by the docs
                // https://developer.android.com/training/gestures/detector
                public boolean onDown(MotionEvent e) {
                    return true;
                }

                public boolean onFling(MotionEvent e1,
                                       MotionEvent e2,
                                       float velocityX,
                                       float velocityY) {
                    if (Math.abs(velocityY) < Math.abs(velocityX))
                        return false;

                    if (velocityY > 0)
                        ClueTabs.this.notifyListenersTabsBarSwipeDown();
                    else
                        ClueTabs.this.notifyListenersTabsBarSwipeUp();

                    return true;
                }
            };

        GestureDetectorCompat tabSwipeDetector = new GestureDetectorCompat(
            tabLayout.getContext(), tabSwipeListener
        );

        tabLayout.setOnTouchListener(new OnTouchListener() {
            public boolean onTouch(View v, MotionEvent e) {
                return tabSwipeDetector.onTouchEvent(e);
            }
        });
    }
}

