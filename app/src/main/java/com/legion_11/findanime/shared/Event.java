package com.legion_11.findanime.shared;

import androidx.annotation.Nullable;

/**
 * Used as a wrapper for data that is exposed via a LiveData that represents an event.
 * Prevents livedata to be consumed 2 times
 * https://stackoverflow.com/a/55212795/15225582
 */

public class Event<T> {

    private T mContent;

    private boolean hasBeenHandled = false;


    public Event( T content) {
        if (content == null) {
            throw new IllegalArgumentException("null values in Event are not allowed.");
        }
        mContent = content;
    }

    @Nullable
    public T getContentIfNotHandled() {
        if (hasBeenHandled) {
            return null;
        } else {
            hasBeenHandled = true;
            return mContent;
        }
    }

    public boolean hasBeenHandled() {
        return hasBeenHandled;
    }
}