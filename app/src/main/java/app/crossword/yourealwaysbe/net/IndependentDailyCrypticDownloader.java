package app.crossword.yourealwaysbe.net;

import java.time.LocalDate;
import java.util.Locale;

import app.crossword.yourealwaysbe.forkyz.ForkyzApplication;
import app.crossword.yourealwaysbe.forkyz.R;

/**
 * Independent Daily Cryptic downloader
 * https://puzzles.independent.co.uk/games/webgl-cryptic-crossword-independent
 * Date = Daily
 */
public class IndependentDailyCrypticDownloader
        extends AbstractJPZDownloader {
    private static final String NAME =
        ForkyzApplication.getInstance().getString(R.string.independent_daily);
    private static final String SUPPORT_URL
        = "https://www.independent.co.uk/donations";

    public IndependentDailyCrypticDownloader() {
        super(
            "https://ams.cdn.arkadiumhosted.com/assets/gamesfeed/independent/daily-crossword/",
            NAME,
            DATE_DAILY,
            SUPPORT_URL
        );
    }

    protected String createUrlSuffix(LocalDate date) {
        return String.format(Locale.US,
                             "c_%02d%02d%02d.xml",
                             date.getYear() % 100,
                             date.getMonthValue(),
                             date.getDayOfMonth());
    }
}
