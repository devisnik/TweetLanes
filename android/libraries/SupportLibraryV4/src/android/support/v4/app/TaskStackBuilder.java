/*
 * Copyright (C) 2012 The Android Open Source Project
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package android.support.v4.app;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.support.v4.content.IntentCompat;
import android.util.Log;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * Utility class for constructing synthetic back stacks for cross-task
 * navigation on Android 3.0 and newer.
 * 
 * <p>
 * In API level 11 (Android 3.0/Honeycomb) the recommended conventions for app
 * navigation using the back key changed. The back key's behavior is local to
 * the current task and does not capture navigation across different tasks.
 * Navigating across tasks and easily reaching the previous task is accomplished
 * through the "recents" UI, accessible through the software-provided Recents
 * key on the navigation or system bar. On devices with the older hardware
 * button configuration the recents UI can be accessed with a long press on the
 * Home key.
 * </p>
 * 
 * <p>
 * When crossing from one task stack to another post-Android 3.0, the
 * application should synthesize a back stack/history for the new task so that
 * the user may navigate out of the new task and back to the Launcher by
 * repeated presses of the back key. Back key presses should not navigate across
 * task stacks.
 * </p>
 * 
 * <p>
 * TaskStackBuilder provides a backward-compatible way to obey the correct
 * conventions around cross-task navigation on the device's version of the
 * platform. On devices running Android 3.0 or newer, calls to the
 * {@link #startActivities()} method or sending the {@link PendingIntent}
 * generated by {@link #getPendingIntent(int, int)} will construct the synthetic
 * back stack as prescribed. On devices running older versions of the platform,
 * these same calls will invoke the topmost activity in the supplied stack,
 * ignoring the rest of the synthetic stack and allowing the back key to
 * navigate back to the previous task.
 * </p>
 * 
 * <div class="special reference"> <h3>About Navigation</h3> For more detailed
 * information about tasks, the back stack, and navigation design guidelines,
 * please read <a href="{@docRoot}
 * guide/topics/fundamentals/tasks-and-back-stack.html">Tasks and Back Stack</a>
 * from the developer guide and <a href="{@docRoot}
 * design/patterns/navigation.html">Navigation</a> from the design guide. </div>
 */
public class TaskStackBuilder implements Iterable<Intent> {
    private static final String TAG = "TaskStackBuilder";

    interface TaskStackBuilderImpl {
        PendingIntent getPendingIntent(Context context, Intent[] intents,
                int requestCode, int flags);
    }

    static class TaskStackBuilderImplBase implements TaskStackBuilderImpl {
        public PendingIntent getPendingIntent(Context context,
                Intent[] intents, int requestCode, int flags) {
            Intent topIntent = intents[intents.length - 1];
            topIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return PendingIntent.getActivity(context, requestCode, topIntent,
                    flags);
        }
    }

    static class TaskStackBuilderImplHoneycomb implements TaskStackBuilderImpl {
        public PendingIntent getPendingIntent(Context context,
                Intent[] intents, int requestCode, int flags) {
            intents[0].addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | IntentCompat.FLAG_ACTIVITY_CLEAR_TASK);
            return TaskStackBuilderHoneycomb.getActivitiesPendingIntent(
                    context, requestCode, intents, flags);
        }
    }

    private static final TaskStackBuilderImpl IMPL;

    static {
        if (Build.VERSION.SDK_INT >= 11) {
            IMPL = new TaskStackBuilderImplHoneycomb();
        } else {
            IMPL = new TaskStackBuilderImplBase();
        }
    }

    private final ArrayList<Intent> mIntents = new ArrayList<Intent>();
    private final Context mSourceContext;

    private TaskStackBuilder(Context a) {
        mSourceContext = a;
    }

    /**
     * Return a new TaskStackBuilder for launching a fresh task stack consisting
     * of a series of activities.
     * 
     * @param context
     *            The context that will launch the new task stack or generate a
     *            PendingIntent
     * @return A new TaskStackBuilder
     */
    public static TaskStackBuilder from(Context context) {
        return new TaskStackBuilder(context);
    }

    /**
     * Add a new Intent to the task stack. The most recently added Intent will
     * invoke the Activity at the top of the final task stack.
     * 
     * @param nextIntent
     *            Intent for the next Activity in the synthesized task stack
     * @return This TaskStackBuilder for method chaining
     */
    public TaskStackBuilder addNextIntent(Intent nextIntent) {
        mIntents.add(nextIntent);
        return this;
    }

    /**
     * Add the activity parent chain as specified by manifest &lt;meta-data&gt;
     * elements to the task stack builder.
     * 
     * @param sourceActivity
     *            All parents of this activity will be added
     * @return This TaskStackBuilder for method chaining
     */
    public TaskStackBuilder addParentStack(Activity sourceActivity) {
        final int insertAt = mIntents.size();
        Intent parent = NavUtils.getParentActivityIntent(sourceActivity);
        while (parent != null) {
            mIntents.add(insertAt, parent);
            try {
                parent = NavUtils.getParentActivityIntent(sourceActivity,
                        parent.getComponent());
            } catch (NameNotFoundException e) {
                Log.e(TAG,
                        "Bad ComponentName while traversing activity parent metadata");
                throw new IllegalArgumentException(e);
            }
        }
        return this;
    }

    /**
     * Add the activity parent chain as specified by manifest &lt;meta-data&gt;
     * elements to the task stack builder.
     * 
     * @param sourceActivityClass
     *            All parents of this activity will be added
     * @return This TaskStackBuilder for method chaining
     */
    public TaskStackBuilder addParentStack(Class<?> sourceActivityClass) {
        final int insertAt = mIntents.size();
        try {
            Intent parent = NavUtils.getParentActivityIntent(mSourceContext,
                    sourceActivityClass);
            while (parent != null) {
                mIntents.add(insertAt, parent);
                parent = NavUtils.getParentActivityIntent(mSourceContext,
                        parent.getComponent());
            }
        } catch (NameNotFoundException e) {
            Log.e(TAG,
                    "Bad ComponentName while traversing activity parent metadata");
            throw new IllegalArgumentException(e);
        }
        return this;
    }

    /**
     * @return the number of intents added so far.
     */
    public int getIntentCount() {
        return mIntents.size();
    }

    /**
     * Get the intent at the specified index. Useful if you need to modify the
     * flags or extras of an intent that was previously added, for example with
     * {@link #addParentStack(Activity)}.
     * 
     * @param index
     *            Index from 0-getIntentCount()
     * @return the intent at position index
     */
    public Intent getIntent(int index) {
        return mIntents.get(index);
    }

    public Iterator<Intent> iterator() {
        return mIntents.iterator();
    }

    /**
     * Start the task stack constructed by this builder. The Context used to
     * obtain this builder must be an Activity.
     * 
     * <p>
     * On devices that do not support API level 11 or higher the topmost
     * activity will be started as a new task. On devices that do support API
     * level 11 or higher the new task stack will be created in its entirety.
     * </p>
     */
    public void startActivities() {
        if (mIntents.isEmpty()) {
            throw new IllegalStateException(
                    "No intents added to TaskStackBuilder; cannot startActivities");
        }

        Intent[] intents = mIntents.toArray(new Intent[mIntents.size()]);
        intents[0].addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | IntentCompat.FLAG_ACTIVITY_CLEAR_TASK
                | IntentCompat.FLAG_ACTIVITY_TASK_ON_HOME);
        if (!ActivityCompat.startActivities((Activity) mSourceContext, intents)) {
            Intent topIntent = intents[intents.length - 1];
            topIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mSourceContext.startActivity(topIntent);
        }
    }

    /**
     * Obtain a {@link PendingIntent} for launching the task constructed by this
     * builder so far.
     * 
     * @param requestCode
     *            Private request code for the sender
     * @param flags
     *            May be {@link PendingIntent#FLAG_ONE_SHOT},
     *            {@link PendingIntent#FLAG_NO_CREATE},
     *            {@link PendingIntent#FLAG_CANCEL_CURRENT},
     *            {@link PendingIntent#FLAG_UPDATE_CURRENT}, or any of the flags
     *            supported by {@link Intent#fillIn(Intent, int)} to control
     *            which unspecified parts of the intent that can be supplied
     *            when the actual send happens.
     * @return The obtained PendingIntent
     */
    public PendingIntent getPendingIntent(int requestCode, int flags) {
        if (mIntents.isEmpty()) {
            throw new IllegalStateException(
                    "No intents added to TaskStackBuilder; cannot getPendingIntent");
        }

        Intent[] intents = mIntents.toArray(new Intent[mIntents.size()]);
        intents[0].addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | IntentCompat.FLAG_ACTIVITY_CLEAR_TASK
                | IntentCompat.FLAG_ACTIVITY_TASK_ON_HOME);
        return IMPL.getPendingIntent(mSourceContext, intents, requestCode,
                flags);
    }
}
