package com.android.custom;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.support.v4.content.ContextCompat;
import android.support.v4.text.TextUtilsCompat;
import android.support.v4.view.ViewCompat;
import android.text.InputType;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import java.util.Locale;

/**
 * Created by Manish on 2/12/16.
 */

public class MyEditText extends EditText {
    //reference
//https://medium.com/@ali.muzaffar/glad-the-article-helped-2a918e396a6#.yb1t0o9yl
    private static final String XML_NAMESPACE_ANDROID = "http://schemas.android.com/apk/res/android";

    private String mMask = null;
    private StringBuilder mMaskChars = null;
    private String mSingleCharHint = null;
    private int mAnimatedType = 0;
    private float mSpace = 24; //24 dp by default, space between the lines
    private float mCharSize;
    private float mNumChars = 4;
    private float mTextBottomPadding = 8; //8dp by default, height of the text from our lines
    private int mMaxLength = 4;
    private RectF[] mLineCoords;
    private float[] mCharBottom;
    private Paint mCharPaint;
    private Paint mLastCharPaint;
    private Paint mSingleCharPaint;
    private Drawable mPinBackground;
    private Rect mTextHeight = new Rect();
    private boolean mIsDigitSquare = false;

    private OnClickListener mClickListener;
    private IEditTextListener mOnPinEnteredListener = null;

    private float mLineStroke = 1; //1dp by default
    private float mLineStrokeSelected = 2; //2dp by default
    private Paint mLinesPaint;
    private boolean mAnimate = false;
    private boolean mHasError = false;
    private ColorStateList mOriginalTextColors;
    private int[][] mStates = new int[][]{
            new int[]{android.R.attr.state_selected}, // selected
            new int[]{android.R.attr.state_active}, // error
            new int[]{android.R.attr.state_focused}, // focused
            new int[]{-android.R.attr.state_focused}, // unfocused
    };

    private int[] mColors = new int[]{
            Color.GREEN,
            Color.RED,
            Color.BLACK,
            Color.GRAY
    };

    private ColorStateList mColorStates = new ColorStateList(mStates, mColors);


    public MyEditText(Context context) {
        super(context);
    }

    public MyEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public MyEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    public MyEditText(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {

        float multi = context.getResources().getDisplayMetrics().density;
        mSpace = multi * mSpace; //convert to pixels for our density
        mLineStroke = multi * mLineStroke; //convert to pixels
        mLineStrokeSelected = multi * mLineStrokeSelected;
        mTextBottomPadding = multi * mTextBottomPadding; //convert to pixels for our density
        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.MyEditText);
        try {
            TypedValue outValue = new TypedValue();
            ta.getValue(R.styleable.MyEditText_pinAnimationType, outValue);
            mAnimatedType = outValue.data;
            mMask = ta.getString(R.styleable.MyEditText_pinCharacterMask);
            mSingleCharHint = ta.getString(R.styleable.MyEditText_pinRepeatedHint);
            mLineStroke = ta.getDimension(R.styleable.MyEditText_pinLineStroke, mLineStroke);
            mLineStrokeSelected = ta.getDimension(R.styleable.MyEditText_pinLineStrokeSelected, mLineStrokeSelected);
            mSpace = ta.getDimension(R.styleable.MyEditText_pinCharacterSpacing, mSpace);
            mTextBottomPadding = ta.getDimension(R.styleable.MyEditText_pinTextBottomPadding, mTextBottomPadding);
            mIsDigitSquare = ta.getBoolean(R.styleable.MyEditText_pinBackgroundIsSquare, mIsDigitSquare);
            mPinBackground = ta.getDrawable(R.styleable.MyEditText_pinBackgroundDrawable);
            ColorStateList colors = ta.getColorStateList(R.styleable.MyEditText_pinLineColors);
            if (colors != null) {
                mColorStates = colors;
            }
        } finally {
            ta.recycle();
        }

        mCharPaint = new Paint(getPaint());
        mLastCharPaint = new Paint(getPaint());
        mSingleCharPaint = new Paint(getPaint());
        mLinesPaint = new Paint(getPaint());
        mLinesPaint.setStrokeWidth(mLineStroke);

        TypedValue outValue = new TypedValue();
        context.getTheme().resolveAttribute(R.attr.colorControlActivated,
                outValue, true);
        int colorSelected = outValue.data;
        mColors[0] = colorSelected;

        int colorFocused = isInEditMode() ? Color.GRAY : ContextCompat.getColor(context, R.color.colorAccent);
        mColors[1] = colorFocused;

        int colorUnfocused = isInEditMode() ? Color.GRAY : ContextCompat.getColor(context, R.color.colorAccent);
        mColors[2] = colorUnfocused;

        setBackgroundResource(0);

        mMaxLength = attrs.getAttributeIntValue(XML_NAMESPACE_ANDROID, "maxLength", 4);
        mNumChars = mMaxLength;


        //Disable copy paste
        super.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            public void onDestroyActionMode(ActionMode mode) {
            }

            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                return false;
            }
        });


        // When tapped, move cursor to end of text.
        super.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                setSelection(getText().length());
                if (mClickListener != null) {
                    mClickListener.onClick(v);
                }
            }
        });

        super.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                setSelection(getText().length());
                return true;
            }
        });


        //If input type is password and no mask is set, use a default mask
        if ((getInputType() & InputType.TYPE_TEXT_VARIATION_PASSWORD) == InputType.TYPE_TEXT_VARIATION_PASSWORD && TextUtils.isEmpty(mMask)) {
            mMask = "\u25CF";
        } else if ((getInputType() & InputType.TYPE_NUMBER_VARIATION_PASSWORD) == InputType.TYPE_NUMBER_VARIATION_PASSWORD && TextUtils.isEmpty(mMask)) {
            mMask = "\u25CF";
        }

        if (!TextUtils.isEmpty(mMask)) {
            mMaskChars = getMaskChars();
        }

        //Height of the characters, used if there is a background drawable
        getPaint().getTextBounds("|", 0, 1, mTextHeight);

        mAnimate = mAnimatedType > -1;

    }

    private StringBuilder getMaskChars() {
        if (mMaskChars == null) {
            mMaskChars = new StringBuilder();
        }
        int textLength = getText().length();
        while (mMaskChars.length() != textLength) {
            if (mMaskChars.length() < textLength) {
                mMaskChars.append(mMask);
            } else {
                mMaskChars.deleteCharAt(mMaskChars.length() - 1);
            }
        }
        return mMaskChars;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mOriginalTextColors = getTextColors();
        if (mOriginalTextColors != null) {
            mLastCharPaint.setColor(mOriginalTextColors.getDefaultColor());
            mCharPaint.setColor(mOriginalTextColors.getDefaultColor());
            mSingleCharPaint.setColor(getCurrentHintTextColor());
        }
        int availableWidth = getWidth() - ViewCompat.getPaddingEnd(this) - ViewCompat.getPaddingStart(this);
        if (mSpace < 0) {
            mCharSize = (availableWidth / (mNumChars * 2 - 1));
        } else {
            mCharSize = (availableWidth - (mSpace * (mNumChars - 1))) / mNumChars;
        }
        mLineCoords = new RectF[(int) mNumChars];
        mCharBottom = new float[(int) mNumChars];
        int startX;
        int bottom = getHeight() - getPaddingBottom();
        int rtlFlag;
        final boolean isLayoutRtl = TextUtilsCompat.getLayoutDirectionFromLocale(Locale.getDefault()) == ViewCompat.LAYOUT_DIRECTION_RTL;
        if (isLayoutRtl) {
            rtlFlag = -1;
            startX = (int) (getWidth() - ViewCompat.getPaddingStart(this) - mCharSize);
        } else {
            rtlFlag = 1;
            startX = ViewCompat.getPaddingStart(this);
        }
        for (int i = 0; i < mNumChars; i++) {
            mLineCoords[i] = new RectF(startX, bottom, startX + mCharSize, bottom);
            if (mPinBackground != null) {
                if (mIsDigitSquare) {
                    mLineCoords[i].top = getPaddingTop();
                    mLineCoords[i].right = startX + mLineCoords[i].height();
                } else {
                    mLineCoords[i].top -= mTextHeight.height() + mTextBottomPadding * 2;
                }
            }

            if (mSpace < 0) {
                startX += rtlFlag * mCharSize * 2;
            } else {
                startX += rtlFlag * (mCharSize + mSpace);
            }
            mCharBottom[i] = mLineCoords[i].bottom - mTextBottomPadding;
        }
    }

    private int getColorForState(int... states) {
        return mColorStates.getColorForState(states, Color.GRAY);
    }

    @Override
    public void setOnClickListener(OnClickListener l) {
        mClickListener = l;
    }

    @Override
    public void setCustomSelectionActionModeCallback(ActionMode.Callback actionModeCallback) {
        throw new RuntimeException("setCustomSelectionActionModeCallback() not supported.");
    }

//
//    @Override
//    protected void onDraw(Canvas canvas) {
//        //super.onDraw(canvas);
//        int availableWidth =
//                getWidth() - getPaddingRight() - getPaddingLeft();
//        if (mSpace < 0) {
//            mCharSize = (availableWidth / (mNumChars * 2 - 1));
//        } else {
//
//            Log.v("qqqqqqq availableWidth", availableWidth + "");
//            Log.v("qqqqqqq WWWW", (availableWidth - (mSpace * (mNumChars - 1))) / mNumChars + "");
//            Log.v("qqqqqqq HHH", (mSpace * (mNumChars - 1)) / mNumChars + "");
//            Log.v("qqqqqqq SSS ", (mSpace * (mNumChars - 1)) + "");
//            mCharSize =
//                    (availableWidth - (mSpace * (mNumChars - 1))) / mNumChars;
//        }
//
//        int startX = getPaddingLeft();
//        int bottom = getHeight() - getPaddingBottom();
//
//        for (int i = 0; i < mNumChars; i++) {
//            canvas.drawLine(
//                    startX, bottom, startX + mCharSize, bottom, getPaint());
//            if (mSpace < 0) {
//                startX += mCharSize * 2;
////                Log.v("qqqqqqq startX", startX + "");
//            } else {
//                startX += mCharSize + mSpace;
////                Log.v("qqqqqqq startxX", startX + "");
//            }
//        }
//    }


    private CharSequence getFullText() {
        if (mMask == null) {
            return getText();
        } else {
            return getMaskChars();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        //super.onDraw(canvas);
        CharSequence text = getFullText();
        int textLength = text.length();
        float[] textWidths = new float[textLength];
        getPaint().getTextWidths(text, 0, textLength, textWidths);

        float hintWidth = 0;
        if (mSingleCharHint != null) {
            float[] hintWidths = new float[mSingleCharHint.length()];
            getPaint().getTextWidths(mSingleCharHint, hintWidths);
            for (float i : hintWidths) {
                hintWidth += i;
            }
        }
        for (int i = 0; i < mNumChars; i++) {
            //If a background for the pin characters is specified, it should be behind the characters.
            if (mPinBackground != null) {
                updateDrawableState(i < textLength, i == textLength);
                mPinBackground.setBounds((int) mLineCoords[i].left, (int) mLineCoords[i].top, (int) mLineCoords[i].right, (int) mLineCoords[i].bottom);
                mPinBackground.draw(canvas);
            }
            float middle = mLineCoords[i].left + mCharSize / 2;
            if (textLength > i) {
                if (!mAnimate || i != textLength - 1) {
                    canvas.drawText(text, i, i + 1, middle - textWidths[i] / 2, mCharBottom[i], mCharPaint);
                } else {
                    canvas.drawText(text, i, i + 1, middle - textWidths[i] / 2, mCharBottom[i], mLastCharPaint);
                }
            } else if (mSingleCharHint != null) {
                canvas.drawText(mSingleCharHint, middle - hintWidth / 2, mCharBottom[i], mSingleCharPaint);
            }
            //The lines should be in front of the text (because that's how I want it).
            if (mPinBackground == null) {
                updateColorForLines(i <= textLength);
                canvas.drawLine(mLineCoords[i].left, mLineCoords[i].top, mLineCoords[i].right, mLineCoords[i].bottom, mLinesPaint);
            }
        }
    }


    /**
     * @param hasTextOrIsNext Is the color for a character that has been typed or is the next
     *                        character to be typed?
     */
    private void updateColorForLines(boolean hasTextOrIsNext) {
        if (mHasError) {
            mLinesPaint.setColor(getColorForState(android.R.attr.state_active));
        } else if (isFocused()) {
            mLinesPaint.setStrokeWidth(mLineStrokeSelected);
            mLinesPaint.setColor(getColorForState(android.R.attr.state_focused));
            if (hasTextOrIsNext) {
                mLinesPaint.setColor(getColorForState(android.R.attr.state_selected));
            }
        } else {
            mLinesPaint.setStrokeWidth(mLineStroke);
            mLinesPaint.setColor(getColorForState(-android.R.attr.state_focused));
        }
    }

    private void updateDrawableState(boolean hasText, boolean isNext) {
        if (mHasError) {
            mPinBackground.setState(new int[]{android.R.attr.state_active});
        } else if (isFocused()) {
            mPinBackground.setState(new int[]{android.R.attr.state_focused});
            if (isNext) {
                mPinBackground.setState(new int[]{android.R.attr.state_focused, android.R.attr.state_selected});
            } else if (hasText) {
                mPinBackground.setState(new int[]{android.R.attr.state_focused, android.R.attr.state_checked});
            }
        } else {
            mPinBackground.setState(new int[]{-android.R.attr.state_focused});
        }
    }

    public boolean isError() {
        return mHasError;
    }

    public void setError(boolean hasError) {
        mHasError = hasError;
    }

    /**
     * Request focus on this PinEntryEditText
     */
    public void focus() {
        requestFocus();

        // Show keyboard
        InputMethodManager inputMethodManager = (InputMethodManager) getContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.showSoftInput(this, 0);
    }

    @Override
    protected void onTextChanged(CharSequence text, final int start, int lengthBefore, final int lengthAfter) {
        setError(false);
        if (mLineCoords == null || !mAnimate) {
            if (mOnPinEnteredListener != null && text.length() == mMaxLength) {
                mOnPinEnteredListener.onPinEntered(text);
            }
            return;
        }

        if (mAnimatedType == -1) {
            invalidate();
            return;
        }

        if (lengthAfter > lengthBefore) {
            if (mAnimatedType == 0) {
                //   animatePopIn();
            } else {
                //animateBottomUp(text, start);
            }
        }
    }
}
