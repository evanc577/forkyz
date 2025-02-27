package app.crossword.yourealwaysbe.puz;

import java.io.Serializable;

public class Box implements Serializable {
    public static final char BLANK = ' ';
    private static final int NOCLUE = -1;
    private static final int NOCOLOR = -1;

    private String responder;
    private boolean across;
    private boolean cheated;
    private boolean down;
    private boolean circled;
    private char response = BLANK;
    private char solution;
    private int clueNumber;
    private int partOfAcrossClueNumber = NOCLUE;
    private int partOfDownClueNumber = NOCLUE;
    private int acrossPosition;
    private int downPosition;

    private boolean barTop = false;
    private boolean barBottom = false;
    private boolean barLeft = false;
    private boolean barRight = false;

    // 24-bit representation 0x00rrggbb
    private int color = NOCOLOR;

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null) {
            return false;
        }

        if (getClass() != obj.getClass()) {
            return false;
        }

        Box other = (Box) obj;

        if (isAcross() != other.isAcross()) {
	return false;
        }

        if (isCheated() != other.isCheated()) {
	return false;
        }

        if (getClueNumber() != other.getClueNumber()) {
	return false;
        }

        if (isDown() != other.isDown()) {
	return false;
        }

        if (isCircled() != other.isCircled()) {
	return false;
        }

        if (getResponder() == null) {
            if (other.getResponder() != null) {
                return false;
            }
        } else if (!responder.equals(other.responder)) {
            return false;
        }

        if (getResponse() != other.getResponse()) {
	return false;
        }

        if (getSolution() != other.getSolution()) {
            return false;
        }

        if (getPartOfAcrossClueNumber() != other.getPartOfAcrossClueNumber()) {
            return false;
        }

        if (getPartOfDownClueNumber() != other.getPartOfDownClueNumber()) {
            return false;
        }

        if (isBarredTop() != other.isBarredTop())
            return false;

        if (isBarredBottom() != other.isBarredBottom())
            return false;

        if (isBarredLeft() != other.isBarredLeft())
            return false;

        if (isBarredRight() != other.isBarredRight())
            return false;

        if (getColor() != other.getColor())
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = (prime * result) + (isAcross() ? 1231 : 1237);
        result = (prime * result) + (isCheated() ? 1231 : 1237);
        result = (prime * result) + getClueNumber();
        result = (prime * result) + (isDown() ? 1231 : 1237);
        result = (prime * result) + (isCircled() ? 1231 : 1237);
        result = (prime * result) + (isBarredTop() ? 1231 : 1237);
        result = (prime * result) + (isBarredBottom() ? 1231 : 1237);
        result = (prime * result) + (isBarredLeft() ? 1231 : 1237);
        result = (prime * result) + (isBarredRight() ? 1231 : 1237);
        result = (prime * result) +
            ((getResponder() == null) ? 0 : getResponder().hashCode());
        result = (prime * result) + getResponse();
        result = (prime * result) + getSolution();
        result = (prime * result) + getPartOfAcrossClueNumber();
        result = (prime * result) + getPartOfDownClueNumber();
        result = (prime * result) + getColor();

        return result;
    }

    @Override
    public String toString() {
        return this.getClueNumber() + this.getSolution() + " ";
    }

    /**
     * @param responder the responder to set
     */
    public void setResponder(String responder) {
        this.responder = responder;
    }

    /**
     * @return the across
     */
    public boolean isAcross() {
        return across;
    }

    /**
     * @param across the across to set
     */
    public void setAcross(boolean across) {
        this.across = across;
    }

    /**
     * @return the cheated
     */
    public boolean isCheated() {
        return cheated;
    }

    /**
     * @param cheated the cheated to set
     */
    public void setCheated(boolean cheated) {
        this.cheated = cheated;
    }

    /**
     * @return the down
     */
    public boolean isDown() {
        return down;
    }

    /**
     * @param down the down to set
     */
    public void setDown(boolean down) {
        this.down = down;
    }

    /**
     * @return if the box is circled
     */
    public boolean isCircled() {
	return circled;
    }

    /**
     * @param circled the circled to set
     */
    public void setCircled(boolean circled) {
	this.circled = circled;
    }

    /**
     * @return the response
     */
    public char getResponse() {
        return response;
    }

    /**
     * @param response the response to set
     */
    public void setResponse(char response) {
        this.response = response;
    }

    /**
     * True if box has solution (i.e. not '\0')
     */
    public boolean hasSolution() {
        return getSolution() != '\0';
    }

    /**
     * @return the solution
     */
    public char getSolution() {
        return solution;
    }

    /**
     * @param solution the solution to set
     */
    public void setSolution(char solution) {
        this.solution = solution;
    }

    /**
     * @return the clueNumber, or 0 for no clue
     */
    public int getClueNumber() {
        return clueNumber;
    }

    /**
     * @param clueNumber the clueNumber to set
     */
    public void setClueNumber(int clueNumber) {
        this.clueNumber = clueNumber;
    }

    /**
     * @return the responder
     */
    public String getResponder() {
        return responder;
    }

    /**
     * @return if the current box is blank
     */
    public boolean isBlank() { return getResponse() == BLANK; }

    public void setBlank() { setResponse(BLANK); }

    /**
     * @param clueNumber across clue that box is a part of
     */
    public void setPartOfAcrossClueNumber(int clueNumber) {
        this.partOfAcrossClueNumber = clueNumber;
    }

    /**
     * @returns across clue that box is a part of (if isPartOfAcross()
     * returns true)
     */
    public int getPartOfAcrossClueNumber() {
        return partOfAcrossClueNumber;
    }

    /**
     * @returns true if box is part of across clue
     */
    public boolean isPartOfAcross() {
        return partOfAcrossClueNumber != NOCLUE;
    }

    /**
     * @param clueNumber down clue that box is a part of
     */
    public void setPartOfDownClueNumber(int clueNumber) {
        this.partOfDownClueNumber = clueNumber;
    }

    /**
     * @returns down clue that box is a part of (if isPartOfDown()
     * returns true)
     */
    public int getPartOfDownClueNumber() {
        return partOfDownClueNumber;
    }

    /**
     * @returns true if box is part of down clue
     */
    public boolean isPartOfDown() {
        return partOfDownClueNumber != NOCLUE;
    }

    /**
     * @param position if part of an across clue, the position in the
     * across word
     */
    public void setAcrossPosition(int position) {
        this.acrossPosition = position;
    }

    /**
     * @return position in the across word if isPartOfAcross returns
     * true
     */
    public int getAcrossPosition() {
        return acrossPosition;
    }

    /**
     * @param position if part of a down clue, the position in the
     * down word
     */
    public void setDownPosition(int position) {
        this.downPosition = position;
    }

    /**
     * @return position in the down word if isPartOfDown returns
     * true
     */
    public int getDownPosition() {
        return downPosition;
    }

    public boolean isBarredTop() { return barTop; }
    public boolean isBarredBottom() { return barBottom; }
    public boolean isBarredLeft() { return barLeft; }
    public boolean isBarredRight() { return barRight; }

    /**
     * True if box has any bars
     */
    public boolean isBarred() {
        return isBarredTop() || isBarredBottom()
            || isBarredLeft() || isBarredRight();
    }

    public void setBarredTop(boolean barTop) {
        this.barTop = barTop;
    }

    public void setBarredBottom(boolean barBottom) {
        this.barBottom = barBottom;
    }

    public void setBarredLeft(boolean barLeft) {
        this.barLeft = barLeft;
    }

    public void setBarredRight(boolean barRight) {
        this.barRight = barRight;
    }

    public boolean hasColor() { return color != NOCOLOR; }

    /**
     * 24-bit 0x00rrggbb when has color
     */
    public int getColor() { return color; }

    /**
     * Set as 24-bit 0x00rrggbb
     */
    public void setColor(int color) {
        this.color = color;
    }
}
