package edu.buffalo.cse.cse486586.simpledynamo;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Formatter;
import java.util.HashMap;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutionException;

import android.app.Activity;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MergeCursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.util.Log;
import  android.database.MatrixCursor;
import android.widget.TextView;


public class SimpleDynamoProvider extends ContentProvider {

    static final String TAG = SimpleDynamoProvider.class.getSimpleName();
    static final String[] REMOTE_PORT = {"11108","11112","11116","11120","11124"};
    static final int SERVER_PORT = 10000;
    private static final String KEY_FIELD = "key";
    private static final String VALUE_FIELD = "value";
    String myPort;

    String failed_port = "";
    ArrayList<String> failed_msgs = new ArrayList<String>();;

    ArrayList<String> present_nodes ;  // contains the active and alive nodes present in the system
    ArrayList<String> hashed_present_nodes = new ArrayList<String>();

    Uri uri = buildUri("content", "edu.buffalo.cse.cse486586.simpledynamo.provider");

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

    public  class HashComparator implements Comparator<String > {   // Comparison using the hash values generated

        public int compare(String s1, String s2){
            try{

                int s1_int = Integer.parseInt(s1);
                int s2_int = Integer.parseInt(s2);

                return (genHash(Integer.toString(s1_int/2)).compareTo(genHash(Integer.toString(s2_int/2)))) ;

            }catch(NoSuchAlgorithmException e){
                Log.e("Inside HashComparator:","Error in genHash");
                return 0;
            }

        }
    }


    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub
        db = dictOpenHelper.getWritableDatabase();
        db.delete(dictOpenHelper.DB_TABLE_NAME,null,null);
        return 0;
    }

    @Override
    public String getType(Uri uri) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        // TODO Auto-generated method stub

        db = dictOpenHelper.getWritableDatabase();
        String curr_node_id ;
        String object_id;

        int myPort_index = present_nodes.indexOf(myPort);

        try{
            Log.i("MyPort----->",myPort);
            Log.i("key in values in insert",values.get("key").toString());
            object_id = genHash(values.get("key").toString());


            Log.v("object_id",object_id);   // object_id - hashed value of key
            curr_node_id = genHash(Integer.toString(Integer.parseInt(myPort)/2));
            int prev_port_index = (myPort_index-1) % present_nodes.size();

            if(prev_port_index<0){
                prev_port_index = prev_port_index + present_nodes.size();
            }


            if( (object_id.compareTo(hashed_present_nodes.get(0))<=0 ) || (object_id.compareTo(hashed_present_nodes.get(hashed_present_nodes.size() - 1)) > 0) ){
                //All exception cases are handled, these do not fall in any of the ring ranges
                String key_part = values.get(KEY_FIELD).toString();
                String value_part = values.get(VALUE_FIELD).toString();

                if(value_part.charAt(value_part.length()-1) != '#'){

                    String valuesToBeSent = key_part + "$" + value_part + "#";  //sending content values as a string ; # indicates that it has to be sent to minimum node

                    new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, valuesToBeSent , myPort);

                    Log.v("Inside insert()","Forward the values to next node::" + valuesToBeSent);

                }
                else{

                    value_part = value_part.substring(0,value_part.length()-1);   // remove # at the end
                    Log.i("Removed #",value_part);

                    ContentValues cv = new ContentValues();

                    cv.put(KEY_FIELD,key_part);
                    cv.put(VALUE_FIELD,value_part);

                    long rowID = db.insert(dictOpenHelper.DB_TABLE_NAME, null, cv);
                    Uri newuri;

                    if (rowID > 0) {
                        newuri = ContentUris.withAppendedId(uri, rowID);
                        getContext().getContentResolver().notifyChange(newuri, null);

                        return newuri;
                    }
                   
                    Log.v("insert", cv.toString());
                    return uri;

                }


            }

            else{

                String key_part = values.get(KEY_FIELD).toString();
                String value_part = values.get(VALUE_FIELD).toString();
               
                if(value_part.charAt(value_part.length()-1) == '{'){

                    value_part = value_part.substring(0,value_part.length()-1);   // remove { at the end

                    ContentValues cv = new ContentValues();

                    cv.put(KEY_FIELD,key_part);
                    cv.put(VALUE_FIELD,value_part);

                    long rowID = db.insert(dictOpenHelper.DB_TABLE_NAME, null, cv);
                    Uri newuri;

                    if (rowID > 0) {
                        newuri = ContentUris.withAppendedId(uri, rowID);
                        getContext().getContentResolver().notifyChange(newuri, null);

                        return newuri;
                    }

                    Log.v("insert", values.toString());
                    return uri;

                }
                else{     // forward the values to the correct node
                    String valuesToBeSent = key_part + "$" + value_part + "=" + node_finder(object_id);  // format key$value=correctnode

                    new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, valuesToBeSent , myPort);

                    Log.v("Inside insert()","Forward the values to next node::" + valuesToBeSent);

                }

            }

        }catch (NoSuchAlgorithmException e){
            Log.e("Inside insert() :", "Error in gen hash");
        }

        return uri;
    }

    @Override
    public boolean onCreate() {
        // TODO Auto-generated method stub

        Context context = getContext();
        dictOpenHelper = new DictOpenHelper(context);  //one-time initialization

        Log.i("REMOTE PORTS",REMOTE_PORT[0]);

        present_nodes = new ArrayList<String>();

        for(String port : REMOTE_PORT){

            present_nodes.add(port);                   // add all 5 nodes

        }

        Collections.sort(present_nodes, new HashComparator()); // SORT the nodes according to the hash values
        Log.i("sorted present nodes",present_nodes.toString());

        hashed_present_nodes = node_hasher(present_nodes);  // Apply SHA-1 hash function to all nodes

        /*
         * Calculate the port number that this AVD listens on.
         * It is just a hack that I came up with to get around the networking limitations of AVDs.
         * The explanation is provided in the PA1 spec.
         *  @author steve ko
         */
        TelephonyManager tel = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        myPort = String.valueOf((Integer.parseInt(portStr) * 2));

        try {
            /*
             * Create a server socket as well as a thread (AsyncTask) that listens on the server
             * port.
             *
             * AsyncTask is a simplified thread construct that Android provides. Please make sure
             * you know how it works by reading
             * http://developer.android.com/reference/android/os/AsyncTask.html
             */
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);

        } catch (IOException e) {
            /*
             * Log is a good way to debug your code. LogCat prints out all the messages that
             * Log class writes.
             *
             * Please read http://developer.android.com/tools/debugging/debugging-projects.html
             * and http://developer.android.com/tools/debugging/debugging-log.html
             * for more information on debugging.
             */
            Log.e(TAG, "Can't create a ServerSocket");
            return false;
        }

        new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "node is back" , myPort);

        return false;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        // TODO Auto-generated method stub

        //SOURCE: https://developer.android.com/reference/android/content/ContentProvider.html#query(android.net.Uri,%20java.lang.String[],%20java.lang.String,%20java.lang.String[],%20java.lang.String)


        // SQLiteQueryBuilder is a helper class that creates the
        // proper SQL syntax for us.
        mdb = dictOpenHelper.getReadableDatabase();

        SQLiteQueryBuilder qBuilder = new SQLiteQueryBuilder();

        qBuilder.setTables(dictOpenHelper.DB_TABLE_NAME);  //Set the table we're querying.
        Log.v("c", mdb.toString());
        Cursor c ;


        if (selection.equals("@") ){       // get all key, value pairs in local node
            Log.v("selection","by @ operator");
            c = qBuilder.query(mdb,
                    projection,
                    null,  //   null will return all rows.
                    selectionArgs,
                    null,
                    null,
                    sortOrder);

        }
        else if(selection.equals("*")){  // get all key, value pairs in all nodes

            Log.v("selection","by * operator");
            c = qBuilder.query(mdb,
                    projection,
                    null,  //   null will return all rows in local node.
                    selectionArgs,
                    null,
                    null,
                    sortOrder);

            MatrixCursor matrixCursor = new MatrixCursor(new String[]{KEY_FIELD,VALUE_FIELD});    // source: http://stackoverflow.com/questions/9917935/adding-rows-into-cursor-manually

            //sending query request to all nodes as all^portnumber

            for(int i=0; i< present_nodes.size();i++){

                if(!myPort.equals(present_nodes.get(i))){

                    String all_request = "all"+ "," + present_nodes.get(i);

                    try{

                        String results = new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, all_request, myPort).get();

                        if(!results.equals("no rows")){

                            String[] result_rows = results.split("\\;");
                            for (String row : result_rows) {

                                String[] kv_parts = row.split("\\?");
                                String res_key = kv_parts[0];
                                String res_value = kv_parts[1];
                                matrixCursor.addRow(new String[]{res_key , res_value} );

                            }

                        }

                    }catch (InterruptedException e) {

                        Log.e("Inside * query:", "InterruptedException");

                    } catch (ExecutionException e) {

                        Log.e("Inside * query:", "ExecutionException");

                    } catch(NullPointerException e){
                        Log.i("Null pointer","dead node");
                    }

                }

            }

            MergeCursor mergeCursor = new MergeCursor(new Cursor[] { matrixCursor, c });

            return mergeCursor;

        }
        else if(selection.charAt(selection.length()-1) =='+'){

            String sel = "key = " + "'" + selection.substring(0,selection.length()-1) + "'" ;
            Log.v("selection",sel);
            Log.i("in + ", sel);
            c = qBuilder.query(mdb,
                    projection,
                    sel,
                    selectionArgs,
                    null,
                    null,
                    sortOrder);
            Log.i("I'm in +","ok");


        }
        else {  // search the range in which the provided selection key belongs to:

            String sel = "key = " + "'" + selection + "'" ;
            Log.v("selection",sel);
            Log.v("search the range",sel);
            Log.v("received_nodes",present_nodes.toString());

            String gen_query = "";
            String sel_str_sent = "";

            try {
                gen_query = genHash(selection);
            } catch (NoSuchAlgorithmException e) {

                Log.e("Inside query()", "error in hash");

            }

            String node_found = node_finder(gen_query);
            sel_str_sent = selection + "&" + node_found;
            c = null;

            //send query request to the minimum node or the required node.
            try {


                String received_str;
                Log.i("Sending query request",node_found);

                try{
                    received_str = new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, sel_str_sent, myPort).get();
                    Log.i("Received query res", "from others: " + received_str);

                }
                catch(Exception e){

                    String new_sel_str_sent = selection + "&" + present_nodes.get((present_nodes.indexOf(node_found) + 2)%present_nodes.size());
                    Log.i("String sent",new_sel_str_sent);
                    received_str = new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, new_sel_str_sent, myPort).get();
                    Log.i("Received query res ", "--exception case: " + received_str);

                }

                // received_str has ~ in  between key and value
                String[] returned_res = received_str.split("\\~");
                MatrixCursor matrixCursor = new MatrixCursor(new String[]{KEY_FIELD,VALUE_FIELD});    // source: http://stackoverflow.com/questions/9917935/adding-rows-into-cursor-manually
                matrixCursor.addRow(new String[]{returned_res[0],returned_res[1]});

                matrixCursor.setNotificationUri(getContext().getContentResolver(), uri);

                return matrixCursor;

            } catch (InterruptedException e) {

                Log.e("Inside query:", "InterruptedException");

            } catch (ExecutionException e) {

                Log.e("Inside query:", "ExecutionException");

            }

        }

        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;

    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        // TODO Auto-generated method stub
        return 0;
    }

    private String genHash(String input) throws NoSuchAlgorithmException {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] sha1Hash = sha1.digest(input.getBytes());
        Formatter formatter = new Formatter();
        for (byte b : sha1Hash) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }

    private Uri buildUri(String scheme, String authority) {  // build the uri
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }


    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];

            /*
             * TODO: Fill in your server code that receives messages and passes them
             * to onProgressUpdate().
             */
            try {

                String strin;   //based on https://docs.oracle.com/javase/tutorial/networking/sockets/ and android doc

                while(true) {
                    Socket socket = serverSocket.accept();
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));


                    if ((strin = in.readLine()) != null ) {

                        if(strin.contains("$")){

                            String received_values = strin;   // strin is present as "key$value"

                            String[] parts = received_values.split("\\$");
                            Log.i("Parts[0]",parts[0]);
                            Log.i("Parts[1]",parts[1]);

                            ContentValues cv = new ContentValues();

                            cv.put(KEY_FIELD,parts[0]);
                            cv.put(VALUE_FIELD,parts[1]);


                            insert(uri,cv);

                            try{

                                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                                out.println("message received by" + myPort);   // send ack that message has been received
                                out.flush();
                            }catch (Exception e) {
                                Log.e(TAG, "not able to send messages :: ack ");
                            }

                        }
                        else if(strin.contains("!")){

                            String parts[] = strin.split("\\!");   // strin : key&port(from where the key came)
                            String port_to_send = parts[1];
                            Log.i("Port_to_Send",port_to_send);
                            String selection_param = parts[0] + "+";  // + indicates - search only in local partition
                            Log.i("Selectparam-servertask ",selection_param);

                            Cursor out_cursor = query(uri,null,selection_param,null,null);

                            //Socket socket_out = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            //Integer.parseInt(port_to_send));


                            int keyIndex = out_cursor.getColumnIndex(KEY_FIELD);  //based on testclicklistener.java
                            int valueIndex = out_cursor.getColumnIndex(VALUE_FIELD);

                            out_cursor.moveToFirst();

                            String returnKey = out_cursor.getString(keyIndex);
                            String returnValue = out_cursor.getString(valueIndex);

                            String res_to_send = returnKey + "~" + returnValue;

                            Log.i("res_to_Send", res_to_send);

                            try {

                                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

                                out.println(res_to_send);
                                out.flush();
                                out_cursor.close();

                                Log.i("Query: Sending ","res_to_send !!!" + res_to_send);

                                socket.close();


                            } catch (IOException e) {
                                Log.e(TAG, "not able to send messages");
                            }
                            socket.close();

                        }
                        else if(strin.equals("all")){

                            String select = "@";
                            Cursor star_cursor = query(uri,null,select,null,null);

                            String temp_result = "";
                            String out_strin = "";

                            if(star_cursor.moveToFirst()){

                                while(star_cursor.isAfterLast() == false){

                                    int keyIndex = star_cursor.getColumnIndex(KEY_FIELD);  //based on testclicklistener.java
                                    int valueIndex = star_cursor.getColumnIndex(VALUE_FIELD);


                                    String returnKey = star_cursor.getString(keyIndex);
                                    String returnValue = star_cursor.getString(valueIndex);

                                    temp_result = returnKey + "?" + returnValue;  // special symbols such as ? used as delimiter between key and value
                                    Log.i("temp_result", temp_result);

                                    out_strin = out_strin + ";" + temp_result;

                                    star_cursor.moveToNext();

                                }

                            }

                            try {


                                if(out_strin.length()>0){
                                    String final_out = out_strin.substring(1,out_strin.length()); // ignore the ; at the start
                                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

                                    out.println(final_out);
                                    out.flush();
                                    star_cursor.close();

                                    Log.i("Query: Sending ","final_out" + final_out);


                                }
                                else{

                                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

                                    out.println("no rows");
                                    out.flush();
                                    star_cursor.close();

                                    Log.i("Query: Sending ","final_out" + "no rows");


                                }
                            } catch (IOException e) {
                                Log.e(TAG, "not able to send messages");
                            }
                            socket.close();



                        }
                        else if(strin.equals("node is back")){  // send the saved messages to recovered port

                            try{


                                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

                                if(failed_msgs.size()!=0){  //send saved messages to recovered node


                                    String saved_string = "";

                                    for(int i =0 ; i < failed_msgs.size(); i++){

                                        saved_string = saved_string + ":" +failed_msgs.get(i);

                                    }

                                    Log.i("saved_string",saved_string);
                                    failed_msgs.clear();

                                    out.println(saved_string);
                                    out.flush();
                                    // socket.close();

                                }
                                else{


                                    out.println("nothing");
                                    out.flush();

                                }

                            }
                            catch(Exception e){
                                Log.e(TAG, "not able to send messages :: socket");
                            }

                            socket.close();

                        }


                    }

                }

            }
            catch(IOException e){
                Log.e(TAG, "not able to receive messages");
            }

            return null;
        }


    }


    /***
     * ClientTask is an AsyncTask that should send a string over the network.
     * It is created by ClientTask.executeOnExecutor() call whenever OnKeyListener.onKey() detects
     * an enter key press event.
     *
     * @author stevko
     *
     */
    private class ClientTask extends AsyncTask<String, Void , String> {

        @Override
        protected String doInBackground(String... msgs) {
            try {

                if(msgs[0].contains("$") && msgs[0].contains("=") ){    // send "values" to various ports

                    String[] parts = msgs[0].split("\\=");

                    String first_port = parts[1];   // key, value pair should be inserted into correct node and next (n-1) nodes ,i.e, 2 nodes

                    int first_port_index = present_nodes.indexOf(first_port);


                    ArrayList<String> ports = new ArrayList<String>();

                    ports.add(present_nodes.get(first_port_index));
                    ports.add(present_nodes.get((first_port_index+ 1)%(present_nodes.size())));
                    ports.add(present_nodes.get((first_port_index+ 2)%(present_nodes.size())));

                    Log.i("ports----%>",ports.toString());

                    for (String port_to_send : ports){

                        String key_value = parts[0];


                        Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                Integer.parseInt(port_to_send));

                        String msgToSend = key_value + "{" ; // to insert in the next n-1 nodes and correct node

                        Log.i("Sending"+ msgToSend , "to" + port_to_send);
                        try {


                            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

                            out.println(msgToSend);
                            out.flush();


                            try{

                                BufferedReader client_in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                                String node_status = client_in.readLine();


                                Log.i("Node Status !:::", node_status);

                            }catch(Exception e){

                                // store messages sent to failed node

                                Log.i("AVD is dead:",port_to_send);
                                Log.i("Failed msg",msgToSend);

                                failed_port = port_to_send;
                                failed_msgs.add(msgToSend);

                            }

                        } catch (IOException e) {
                            Log.e(TAG, "not able to send messages :: failure handling");
                        }
                        socket.close();

                    }

                }
                else if(msgs[0].contains("#")&& msgs[0].contains("$")){

                    ArrayList<String> ports = new ArrayList<String>();
                    ports.add(present_nodes.get(0));
                    ports.add(present_nodes.get(1));
                    ports.add(present_nodes.get(2));

                    for(String port_to_Send : ports){

                        Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                Integer.parseInt(port_to_Send));

                        Log.i("Sending -> "+ msgs[0], "to" + port_to_Send);

                        String msgToSend = msgs[0] ;



                        try {  // failure handling while sending to minimum node

                            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

                            out.println(msgToSend);
                            out.flush();

                            try{

                                BufferedReader client_in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                                String node_status = client_in.readLine();


                                Log.i("Node Status !:::", node_status);

                            }catch(Exception e){

                                // store messages sent to failed node

                                Log.i("AVD is dead:",port_to_Send);
                                Log.i("Failed msg",msgToSend);

                                failed_port = port_to_Send;
                                failed_msgs.add(msgToSend);

                            }

                        } catch (IOException e) {
                            Log.e(TAG, "not able to send messages :: min node - fail handling");
                        }
                        socket.close();

                    }

                }
                else if(msgs[0].contains("&")  ){

                    String[] query_part = msgs[0].split("\\&");  // query parts: key&minimumnode OR key&node
                    Log.i("query_parts",query_part.toString());
                    String port_to_send = query_part[1];
                    String key_part = query_part[0];
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(port_to_send));

                    String msgToSend = key_part + "!" + myPort ;  // send as key ! myport


                    try {

                        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                        BufferedReader client_in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                        String res_string;


                        out.println(msgToSend);
                        out.flush();


                        try{


                            res_string = client_in.readLine();
                            Log.i("Got res_string as:",res_string);

                            return res_string;

                        }catch(Exception e){
                            socket.close();

                            port_to_send = present_nodes.get((present_nodes.indexOf(query_part[1]) + 2)%present_nodes.size());
                            socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                    Integer.parseInt(port_to_send));


                            out = new PrintWriter(socket.getOutputStream(), true);
                            client_in = new BufferedReader(new InputStreamReader(socket.getInputStream()));


                            out.println(msgToSend);
                            out.flush();

                            res_string = client_in.readLine();
                            Log.i("res_string-failure as:",res_string);


                            return res_string;


                        }


                    } catch (IOException e) {
                        Log.e(TAG, "not able to send messages");
                    }
                    socket.close();



                }
                else if(msgs[0].contains(",")){

                    String[] parts = msgs[0].split("\\,");
                    Log.i("port_to_Send in *",parts[1]);
                    String port_to_send = parts[1];
                    String msgToSend = parts[0];  //msg is "all"
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(port_to_send));


                    try {

                        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                        BufferedReader client_in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                        String full_string;


                        out.println(msgToSend);
                        out.flush();


                        try{
                            full_string = client_in.readLine();
                            Log.i("Got full_string as:",full_string);
                            return full_string;
                        }
                        catch(Exception e){

                            Log.i("The node is dead",port_to_send);
                            return null;
                        }




                    } catch (IOException e) {
                        Log.e(TAG, "not able to send messages");
                    }
                    socket.close();

                }
                else if (msgs[0].equals("node is back")){  // send the message as such esp "Node is back"


                    for(String port_to_Send : REMOTE_PORT){

                        if(port_to_Send.equals(myPort)){
                            continue;    // send the message to all except itself
                        }

                        Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                Integer.parseInt(port_to_Send));

                        Log.i("Sending -> "+ msgs[0], "to" + port_to_Send);

                        String msgToSend = msgs[0] ;

                        try {


                            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

                            out.println(msgToSend);
                            out.flush();

                            BufferedReader client_in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                            String str;

                            Log.e("entered","here");

                            if((str = client_in.readLine()) != null){
                                Log.e("hereee:",str);

                                if(!str.equals("nothing")){

                                    // insert the recovered messages


                                    String[] saved_full_string = str.split("\\:");

                                    for(String saved_full_str : saved_full_string){

                                        if(saved_full_str.equals("")){
                                            continue;
                                        }


                                        String[] parts = saved_full_str.split("\\$");
                                        Log.i("Parts[0] in recovery",parts[0]);
                                        Log.i("Parts[1] in recovery",parts[1]);

                                        ContentValues cv = new ContentValues();

                                        cv.put(KEY_FIELD,parts[0]);
                                        cv.put(VALUE_FIELD,parts[1]);


                                        insert(uri,cv);
                                    }

                                }
                                else{

                                    Log.e("Got"+ str ,"from" + port_to_Send);

                                }

                            }

                        } catch (IOException e) {
                            Log.e(TAG, "not able to send messages");
                        }
                        socket.close();

                    }

                }

            } catch (UnknownHostException e) {
                Log.e(TAG, "ClientTask UnknownHostException");
            } catch (IOException e) {
                Log.e(TAG, "ClientTask socket IOException");
            }
            return null;

        }


    }


    private ArrayList<String> node_hasher(ArrayList<String> input){

        ArrayList<String> hashed_output = new ArrayList<String>();

        for(int i = 0; i< present_nodes.size();i++){
            try{
                hashed_output.add(genHash(Integer.toString(Integer.parseInt(input.get(i))/2)));
                Log.i("Hashed_op-node_hasher:",hashed_output.toString());

            }catch(NoSuchAlgorithmException e){
                Log.e("error in:","hashed_nodes");
            }

        }
        return hashed_output;

    }


    private String node_finder(String input){   // find the correct node for insert and query operations

        int i = 0;
        int max = hashed_present_nodes.size()-2;

        while(i<= max){
            if(input.compareTo(hashed_present_nodes.get(i))> 0 && input.compareTo(hashed_present_nodes.get(i+1))<= 0  ){
                Log.i("in  node finder:",present_nodes.get(i+1));
                return present_nodes.get(i+1);

            }
            i++;
        }


        return present_nodes.get(0);
    }


}
