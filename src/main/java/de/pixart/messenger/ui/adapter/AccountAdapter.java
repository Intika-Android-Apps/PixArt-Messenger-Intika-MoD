package de.pixart.messenger.ui.adapter;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.v7.widget.SwitchCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import de.pixart.messenger.Config;
import de.pixart.messenger.R;
import de.pixart.messenger.entities.Account;
import de.pixart.messenger.ui.ManageAccountActivity;
import de.pixart.messenger.ui.XmppActivity;
import de.pixart.messenger.ui.util.StyledAttributes;
import de.pixart.messenger.utils.UIHelper;

public class AccountAdapter extends ArrayAdapter<Account> {

    private XmppActivity activity;
    private boolean showStateButton;

    public AccountAdapter(XmppActivity activity, List<Account> objects, boolean showStateButton) {
        super(activity, 0, objects);
        this.activity = activity;
        this.showStateButton = showStateButton;
    }

    public AccountAdapter(XmppActivity activity, List<Account> objects) {
        super(activity, 0, objects);
        this.activity = activity;
        this.showStateButton = true;
    }

    @NonNull
    @Override
    public View getView(int position, View view, @NonNull ViewGroup parent) {
        final Account account = getItem(position);
        if (view == null) {
            LayoutInflater inflater = (LayoutInflater) getContext()
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = inflater.inflate(R.layout.account_row, parent, false);
        }
        TextView jid = view.findViewById(R.id.account_jid);
        if (Config.DOMAIN_LOCK != null) {
            jid.setText(account.getJid().getLocal());
        } else {
            jid.setText(account.getJid().asBareJid().toString());
        }
        TextView statusView = view.findViewById(R.id.account_status);
        ImageView imageView = view.findViewById(R.id.account_image);
        loadAvatar(account,imageView);
        statusView.setText(getContext().getString(account.getStatus().getReadableId()));
        switch (account.getStatus()) {
            case ONLINE:
                statusView.setTextColor(StyledAttributes.getColor(activity, R.attr.TextColorOnline));
                break;
            case DISABLED:
            case CONNECTING:
                statusView.setTextColor(StyledAttributes.getColor(activity, android.R.attr.textColorSecondary));
                break;
            default:
                statusView.setTextColor(StyledAttributes.getColor(activity, R.attr.TextColorError));
                break;
        }
        final SwitchCompat tglAccountState = view.findViewById(R.id.tgl_account_status);
        final boolean isDisabled = (account.getStatus() == Account.State.DISABLED);
        tglAccountState.setOnCheckedChangeListener(null);
        tglAccountState.setChecked(!isDisabled);
        if (this.showStateButton) {
            tglAccountState.setVisibility(View.VISIBLE);
        } else {
            tglAccountState.setVisibility(View.GONE);
        }
        tglAccountState.setOnCheckedChangeListener((compoundButton, b) -> {
            if (b == isDisabled && activity instanceof OnTglAccountState) {
                ((OnTglAccountState) activity).onClickTglAccountState(account, b);
            }
        });
        return view;
    }

    public interface OnTglAccountState {
        void onClickTglAccountState(Account account, boolean state);
    }

    public static boolean cancelPotentialWork(Account account, ImageView imageView) {
        final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);

        if (bitmapWorkerTask != null) {
            final Account oldAccount = bitmapWorkerTask.account;
            if (oldAccount == null || account != oldAccount) {
                bitmapWorkerTask.cancel(true);
            } else {
                return false;
            }
        }
        return true;
    }

    private static BitmapWorkerTask getBitmapWorkerTask(ImageView imageView) {
        if (imageView != null) {
            final Drawable drawable = imageView.getDrawable();
            if (drawable instanceof AsyncDrawable) {
                final AsyncDrawable asyncDrawable = (AsyncDrawable) drawable;
                return asyncDrawable.getBitmapWorkerTask();
            }
        }
        return null;
    }

    public void loadAvatar(Account account, ImageView imageView) {
        if (cancelPotentialWork(account, imageView)) {
            final Bitmap bm = activity.avatarService().get(account, activity.getPixel(56), true);
            if (bm != null) {
                cancelPotentialWork(account, imageView);
                imageView.setImageBitmap(bm);
                imageView.setBackgroundColor(0x00000000);
            } else {
                imageView.setBackgroundColor(UIHelper.getColorForName(account.getJid().asBareJid().toString()));
                imageView.setImageDrawable(null);
                final BitmapWorkerTask task = new BitmapWorkerTask(imageView);
                final AsyncDrawable asyncDrawable = new AsyncDrawable(activity.getResources(), null, task);
                imageView.setImageDrawable(asyncDrawable);
                try {
                    task.executeOnExecutor(BitmapWorkerTask.THREAD_POOL_EXECUTOR, account);
                } catch (final RejectedExecutionException ignored) {
                }
            }
        }
    }

    static class AsyncDrawable extends BitmapDrawable {
        private final WeakReference<BitmapWorkerTask> bitmapWorkerTaskReference;

        public AsyncDrawable(Resources res, Bitmap bitmap, BitmapWorkerTask bitmapWorkerTask) {
            super(res, bitmap);
            bitmapWorkerTaskReference = new WeakReference<>(bitmapWorkerTask);
        }

        public BitmapWorkerTask getBitmapWorkerTask() {
            return bitmapWorkerTaskReference.get();
        }
    }

    class BitmapWorkerTask extends AsyncTask<Account, Void, Bitmap> {
        private final WeakReference<ImageView> imageViewReference;
        private Account account = null;

        public BitmapWorkerTask(ImageView imageView) {
            imageViewReference = new WeakReference<>(imageView);
        }

        @Override
        protected Bitmap doInBackground(Account... params) {
            this.account = params[0];
            return activity.avatarService().get(this.account, activity.getPixel(56), isCancelled());
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (bitmap != null && !isCancelled()) {
                final ImageView imageView = imageViewReference.get();
                if (imageView != null) {
                    imageView.setImageBitmap(bitmap);
                    imageView.setBackgroundColor(0x00000000);
                }
            }
        }
    }
}
