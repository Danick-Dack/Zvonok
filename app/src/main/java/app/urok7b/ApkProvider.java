package app.urok7b;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Отдаёт ровно один файл — APK самого приложения, только для чтения и только тому,
 * кому пользователь сам отправил его через «Поделиться». Так приложение можно передать
 * другу без интернета: по Bluetooth, Quick Share, в Telegram или WhatsApp.
 * Провайдер не экспортирован; доступ выдаётся временно через FLAG_GRANT_READ_URI_PERMISSION.
 */
public class ApkProvider extends ContentProvider {

    static final String AUTHORITY = "app.urok7b.apk";
    static final String NAME = "Zvonok-165.apk";
    static final Uri URI = Uri.parse("content://" + AUTHORITY + "/" + NAME);
    static final String MIME = "application/vnd.android.package-archive";

    private File apk() {
        return new File(getContext().getApplicationInfo().sourceDir);
    }

    private void check(Uri uri) throws FileNotFoundException {
        if (!NAME.equals(uri.getLastPathSegment())) throw new FileNotFoundException();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        check(uri);
        if (!"r".equals(mode)) throw new SecurityException("read only");
        return ParcelFileDescriptor.open(apk(), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        if (!NAME.equals(uri.getLastPathSegment())) return null;
        MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        c.addRow(new Object[]{NAME, apk().length()});
        return c;
    }

    @Override
    public String getType(Uri uri) {
        return NAME.equals(uri.getLastPathSegment()) ? MIME : null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] args) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) {
        throw new UnsupportedOperationException();
    }
}
