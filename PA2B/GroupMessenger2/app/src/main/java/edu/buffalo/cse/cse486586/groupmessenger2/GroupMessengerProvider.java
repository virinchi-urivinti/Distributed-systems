package edu.buffalo.cse.cse486586.groupmessenger2;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.util.Log;

/**
 * GroupMessengerProvider is a key-value table. Once again, please note that we do not implement
 * full support for SQL as a usual ContentProvider does. We re-purpose ContentProvider's interface
 * to use it as a key-value table.
 * 
 * Please read:
 * 
 * http://developer.android.com/guide/topics/providers/content-providers.html
 * http://developer.android.com/reference/android/content/ContentProvider.html
 * 
 * before you start to get yourself familiarized with ContentProvider.
 * 
 * There are two methods you need to implement---insert() and query(). Others are optional and
 * will not be tested.
 * 
 * @author stevko
 *
 */
public class GroupMessengerProvider extends ContentProvider {

    private SQLiteDatabase db;
    private SQLiteDatabase mdb;
    private DictOpenHelper dictOpenHelper;


    public class DictOpenHelper extends SQLiteOpenHelper {      //SOURCE: https://developer.android.com/guide/topics/data/data-storage.html#db

        private static final int DATABASE_VERSION = 2;
        private static final String DB_TABLE_NAME = "KEY_VALUE";
        private static final String KEY_FIELD = "key";
        private static final String VALUE_FIELD = "value";
        private static final String DATABASE_NAME = "key_value.db";
        private static final String DB_TABLE_CREATE =
                "CREATE TABLE " + DB_TABLE_NAME + " (" +
                        KEY_FIELD + " TEXT , " +
                        VALUE_FIELD + " TEXT, " +
                        "UNIQUE(" + KEY_FIELD + ") ON CONFLICT REPLACE);";


        DictOpenHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            //db.execSQL("DROP TABLE IF EXISTS" + DB_TABLE_NAME);
            //db.delete(DB_TABLE_NAME, null, null);
            db.execSQL(DB_TABLE_CREATE);

        }


        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

            //CopyNewDatabaseFromAsset();
            db.execSQL("DROP TABLE IF EXISTS" + DB_TABLE_NAME);
            onCreate(db);


        }

    }





    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // You do not need to implement this.
        return 0;
    }

    @Override
    public String getType(Uri uri) {
        // You do not need to implement this.
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        /*
         * TODO: You need to implement this method. Note that values will have two columns (a key
         * column and a value column) and one row that contains the actual (key, value) pair to be
         * inserted.
         * 
         * For actual storage, you can use any option. If you know how to use SQL, then you can use
         * SQLite. But this is not a requirement. You can use other storage options, such as the
         * internal storage option that we used in PA1. If you want to use that option, please
         * take a look at the code for PA1.
         */

        db = dictOpenHelper.getWritableDatabase();

        long rowID = db.insert(dictOpenHelper.DB_TABLE_NAME, null, values);
        Uri newuri;

        if (rowID > 0) {
            newuri = ContentUris.withAppendedId(uri, rowID);
            getContext().getContentResolver().notifyChange(newuri, null);

            return newuri;
        }




        Log.v("insert", values.toString());
        return uri;
    }

    @Override
    public boolean onCreate() {
        // If you need to perform any one-time initialization task, please do it here.
        Context context = getContext();
        dictOpenHelper = new DictOpenHelper(context);  //one-time initialization

        return true;

    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // You do not need to implement this.
        return 0;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        /*
         * TODO: You need to implement this method. Note that you need to return a Cursor object
         * with the right format. If the formatting is not correct, then it is not going to work.
         *
         * If you use SQLite, whatever is returned from SQLite is a Cursor object. However, you
         * still need to be careful because the formatting might still be incorrect.
         *
         * If you use a file storage option, then it is your job to build a Cursor * object. I
         * recommend building a MatrixCursor described at:
         * http://developer.android.com/reference/android/database/MatrixCursor.html
         */

        //SOURCE: https://developer.android.com/reference/android/content/ContentProvider.html#query(android.net.Uri,%20java.lang.String[],%20java.lang.String,%20java.lang.String[],%20java.lang.String)


        // SQLiteQueryBuilder is a helper class that creates the
        // proper SQL syntax for us.
        mdb = dictOpenHelper.getReadableDatabase();

        SQLiteQueryBuilder qBuilder = new SQLiteQueryBuilder();

        qBuilder.setTables(dictOpenHelper.DB_TABLE_NAME);  //Set the table we're querying.
        Log.v("c", mdb.toString());
        // Make the query.
        String sel = "key = " + "'" + selection + "'" ;
        Log.v("sel",sel);
        Cursor c = qBuilder.query(mdb,
                projection,
                sel,
                selectionArgs,
                null,
                null,
                sortOrder);



        c.setNotificationUri(getContext().getContentResolver(), uri);

        return c;




    }
}
