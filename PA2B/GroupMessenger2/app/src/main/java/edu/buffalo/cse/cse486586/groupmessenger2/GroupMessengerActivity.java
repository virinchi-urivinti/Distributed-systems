package edu.buffalo.cse.cse486586.groupmessenger2;



import java.net.ServerSocket;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;


import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.Menu;
import android.widget.TextView;
import android.content.Context;
import android.telephony.TelephonyManager;
import android.os.AsyncTask;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.net.Uri.Builder ;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 *
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity {

    static final String TAG = GroupMessengerActivity.class.getSimpleName();
    static final String[] REMOTE_PORT = {"11108","11112","11116","11120","11124"};
    private static final String KEY_FIELD = "key";
    private static final String VALUE_FIELD = "value";
    int i ;
    int seq_proposed = 0 ;
    int final_seq = -1 ;
    int num;
    int cnt = 0;
    int seq_agreed ;
    String string_msg;
    int num_alive = 5;

    ArrayList proposed_arr = new ArrayList();
    ArrayList sockets = new ArrayList();

    ContentValues values = new ContentValues();


    private  Uri mUri ;
    ContentResolver contentResolver;



    static final int SERVER_PORT = 10000;




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);


        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority("edu.buffalo.cse.cse486586.groupmessenger2.provider");    //based on onPTestClickListener.java
        uriBuilder.scheme("content");

        mUri =  uriBuilder.build();
        contentResolver = getContentResolver();
        i=0;
        Log.v("uri", mUri.toString());

        /*
         * Calculate the port number that this AVD listens on.
         * It is just a hack that I came up with to get around the networking limitations of AVDs.
         * The explanation is provided in the PA1 spec.
         *  @author steve ko
         */
        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));


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
            return;
        }



        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */
        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());

        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));

        /*
         * TODO: You need to register and implement an OnClickListener for the "Send" button.
         * In your implementation you need to get the message from the input box (EditText)
         * and send it to other AVDs.
         */


         /*
         * Retrieve a pointer to the input box (EditText) defined in the layout
         * XML file (res/layout/main.xml).
         *
         * This is another example of R class variables. R.id.edit_text refers to the EditText UI
         * element declared in res/layout/main.xml. The id of "edit_text" is given in that file by
         * the use of "android:id="@+id/edit_text""
         */
        final EditText editText = (EditText) findViewById(R.id.editText1);
         /*
         * Register an OnKeyListener for the input box. OnKeyListener is an event handler that
         * processes each key event. The purpose of the following code is to detect an enter key
         * press event, and create a client thread so that the client thread can send the string
         * in the input box over the network.
         */

        //ONKEY EVENT
        findViewById(R.id.button4).setOnClickListener( new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                String msg = editText.getText().toString() + "\n";
                editText.setText(""); // This is one way to reset the input box.
                TextView localTextView = (TextView) findViewById(R.id.textView1);
                localTextView.append("\t" + msg); // This is one way to display a string.
                TextView remoteTextView = (TextView) findViewById(R.id.textView1);
                remoteTextView.append("\n");

                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, myPort);
                return;


            }

        });


    }

    /***
     * ServerTask is an AsyncTask that should handle incoming messages. It is created by
     * ServerTask.executeOnExecutor() call in SimpleMessengerActivity.
     *
     * Please make sure you understand how AsyncTask works by reading
     * http://developer.android.com/reference/android/os/AsyncTask.html
     *
     * @author stevko
     *
     */
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
                    PrintWriter serv_out = new PrintWriter(socket.getOutputStream(), true);



                    if ((strin = in.readLine()) != null ) {

                        if(seq_proposed <= final_seq ){
                            seq_proposed = final_seq + 1;
                        }

                        serv_out.println(seq_proposed);
                        serv_out.flush();
                        string_msg = strin;
                        publishProgress(strin);
                        Log.v("Published string", strin);





                    }
                    while((strin = in.readLine()) != null ){         // http://stackoverflow.com/questions/36135983/cant-send-multiple-messages-via-socket
                        try{
                            final_seq = Integer.parseInt(strin);
                            values.put(KEY_FIELD, strin);
                            values.put(VALUE_FIELD, string_msg);
                            Log.i("final no. !",strin);
                            contentResolver.insert(mUri, values);


                            Log.i("in the final_seq loop", strin);
                            socket.close();
                            break;
                        }
                        catch (NumberFormatException e)
                        {
                            Log.e(TAG, "empty received");
                            continue;
                        }



                    }

                }



            }
            catch(IOException e){
                Log.e(TAG, "not able to receive messages");
            }


            return null;
        }

        protected void onProgressUpdate(String...strings) {
            /*
             * The following code displays what is received in doInBackground().
             */
            String strReceived = strings[0].trim();
            TextView remoteTextView = (TextView) findViewById(R.id.textView1);   //using same textview for both received and sent messages
            remoteTextView.append(strReceived + "\t\n");
            TextView localTextView = (TextView) findViewById(R.id.textView1);
            localTextView.append("\n");




            return;
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
    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {
            try {

                for(int i=0; i< REMOTE_PORT.length ; i++ ) {


                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(REMOTE_PORT[i]));
                    sockets.add(socket);
                    String msgToSend = msgs[0];
                    String str;
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    BufferedReader client_in = new BufferedReader(new InputStreamReader(socket.getInputStream()));



                /*
                 * TODO: Fill in your client code that sends out a message.
                 */
                    try {



                        out.println(msgToSend);
                        Log.i("trying to send",msgToSend);
                        out.flush();


                        try{
                            str = client_in.readLine();
                            num = Integer.parseInt(str);
                            proposed_arr.add(num);

                            Log.i("reading client",Integer.toString(num));

                        }catch(Exception e){
                            num_alive = 4;
                            Log.i("AVD is dead","nullpointer");

                        }









                    } catch (Exception e) {
                        Log.e(TAG, "not able to send messages");
                    }

                }


                if(proposed_arr.size()== num_alive){


                    try{
                        seq_agreed = (Integer) Collections.max(proposed_arr);
                    }catch(RuntimeException e){
                        Log.e(TAG, "no elements yet");
                    }

                    Log.i("proposed arr", proposed_arr.toString());
                    Log.i("seq_Agreed", Integer.toString(seq_agreed));


                    int i = 0;
                    while( i< REMOTE_PORT.length  ) {


                        Log.i("sending maxxx","to" + REMOTE_PORT[i]);
                        Socket sock = (Socket)sockets.get(i);
                        PrintWriter out = new PrintWriter(sock.getOutputStream(), true);


                        out.println(Integer.toString(seq_agreed));

                        out.flush();
                        i++;





                    }

                    proposed_arr.clear();
                    sockets.clear();



                }








            } catch (UnknownHostException e) {
                Log.e(TAG, "ClientTask UnknownHostException");
            } catch (IOException e) {
                Log.e(TAG, "ClientTask socket IOException");
            }

            return null;
        }







    }







    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }
}
