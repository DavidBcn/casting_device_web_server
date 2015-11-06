package dropbox.nordicloop.com.dropbox;

import android.app.Activity;
import android.content.Intent;
import android.os.Environment;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import com.dropbox.sync.android.DbxAccount;
import com.dropbox.sync.android.DbxAccountManager;
import com.dropbox.sync.android.DbxException;
import com.dropbox.sync.android.DbxFile;
import com.dropbox.sync.android.DbxFileInfo;
import com.dropbox.sync.android.DbxFileSystem;
import com.dropbox.sync.android.DbxPath;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import android.util.Log;
import java.io.*;
import java.util.*;

public class MainActivity extends AppCompatActivity {
    private static final String[] supportedFormats = {".jpg"};
    private static final String ROOT = "";
    private DbxAccountManager mDbxAcctMgr;
    private WebView mWebView;
    private Button mLinkedButton;
    private boolean mAccountLinked;
    private ListView mList;
    private List<DbxFileInfo> mCurrentPathFiles;
    private DbxPath mCurrentPath = new DbxPath(ROOT);
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        try {
            WebServer server = new WebServer(getApplicationContext());
        } catch(IOException ioe) {
            Log.w("Httpd", "The server could not start.");
        }
        Log.w("Httpd", "Web server initialized.");

        String ua = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_10_1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2227.1 Safari/537.36";
        mDbxAcctMgr = DbxAccountManager.getInstance(getApplicationContext(), "2xxwu1ua65cgorf", "03qwwtsa648bmch");

        mList = (ListView) findViewById(R.id.listView);

        findViewById(R.id.button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                AppKeyPair appKeyPair = new AppKeyPair("p91llbgxbkkjc6l", "2v8uwgthreqm209");
//                AndroidAuthSession session = new AndroidAuthSession(appKeyPair);
//                mDropboxApi = new DropboxAPI<>(session);
//                mDropboxApi.getSession().startOAuth2Authentication(getApplicationContext());
//                getImage();
                mCurrentPathFiles = getTree(new DbxPath(""));
                refreshList(toStringList(mCurrentPathFiles).toArray());
            }
        });

        mLinkedButton = (Button) findViewById(R.id.linkAccount);
        mLinkedButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onClickLinkToDropbox(v);
            }
        });

        mList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (!isRootFolder() && position == 0) {
                    // Moving to Parent folder
                    mCurrentPathFiles = getTree(mCurrentPath.getParent());
                } else {
                    if (!isRootFolder()) {
                        --position;
                    }

                    DbxFileInfo dbxFileInfo = mCurrentPathFiles.get(position);
                    if (!dbxFileInfo.isFolder) {
                        Toast.makeText(getApplicationContext(), "This is not a folder is a file!", Toast.LENGTH_SHORT).show();
                        return;
                    } else {
                        mCurrentPathFiles = getTree(dbxFileInfo.path);
                    }
                }
                refreshList(toStringList(mCurrentPathFiles).toArray());
            }
        });

        mList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if (isRootFolder() || position != 0) {
                    if (!isRootFolder()) {
                        --position;
                    }

                    DbxFileInfo dbxFileInfo = mCurrentPathFiles.get(position);
                    if (dbxFileInfo.isFolder) {
                        getImages(dbxFileInfo.path);
                    } else {
                        Toast.makeText(getApplicationContext(), "This is not a folder is a file!", Toast.LENGTH_SHORT).show();
                    }

                    return true;
                }
                return false;
            }
        });

        refreshUi();
    }

    private boolean isRootFolder(){
        return mCurrentPath.compareTo(new DbxPath(ROOT)) == 0;
    }

    private List<String> toStringList(List<DbxFileInfo> dbxFileInfos) {
        List<String> namesList = new ArrayList<>();
        if(dbxFileInfos != null) {
            if(!isRootFolder()) {
                namesList.add(".. Parent folder");
            }
            for(DbxFileInfo fileInfo : dbxFileInfos) {
                namesList.add(fileInfo.path.getName());
            }
        }
        return namesList;
    }

    private void refreshList(Object[] sArray) {
        mList.setAdapter(new ArrayAdapter(this, android.R.layout.simple_list_item_1, sArray));
    }

    private void refreshUi(){
        DbxAccount account = mDbxAcctMgr.getLinkedAccount();
        mAccountLinked = account != null;
        mLinkedButton.setEnabled(!mAccountLinked);
    }

    static final int REQUEST_LINK_TO_DBX = 0;  // This value is up to you

    public void onClickLinkToDropbox(View view) {
        mDbxAcctMgr.startLink((AppCompatActivity)this, REQUEST_LINK_TO_DBX);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_LINK_TO_DBX) {
            if (resultCode == Activity.RESULT_OK) {
                // ... Start using Dropbox files.
                refreshUi();
                getImage();
            } else {
                // ... Link failed or was cancelled by the user.
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    /**
     * Return the list of the DBXFileInfo that represents the tree of {@param path} if there is an account linked.
     * Otherwise returns null.
     */
    private List<DbxFileInfo> getTree(DbxPath path){
        mCurrentPath = path;
        List<DbxFileInfo> files = null;
        try {
            final DbxFileSystem fileSystem = DbxFileSystem.forAccount(mDbxAcctMgr.getLinkedAccount());
            files = fileSystem.listFolder(path);
        } catch (DbxException.Unauthorized unauthorized) {
            unauthorized.printStackTrace();
        } catch (DbxException e) {
            e.printStackTrace();
        }
        return files;
    }

    private void getImage() {
        try {
            DbxAccount account = mDbxAcctMgr.getLinkedAccount();

            if(account != null) {
                final DbxFileSystem fileSystem = DbxFileSystem.forAccount(mDbxAcctMgr.getLinkedAccount());
                final DbxPath path = new DbxPath("/David kite");
                final List<DbxFileInfo> files = fileSystem.listFolder(path);
                Runnable runnable = new Runnable() {
                    @Override
                    public void run() {
                        try {

                            File root = Environment.getExternalStorageDirectory();

                            FileOutputStream f = new FileOutputStream(new File(root, "holaa"));
                            DbxFile file = fileSystem.open(files.get(0).path);

                            InputStream in = file.getReadStream();

                            byte[] buffer = new byte[1024];
                            int len1 = 0;
                            while ((len1 = in.read(buffer)) > 0) {
                                f.write(buffer, 0, len1);
                            }
                            f.close();

//                            Document doc = Jsoup.connect(url).get();
//                            Element image = doc.select("img.preview-image").first();
//                        Log.i("david", image.attr("src"));

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
//                                mWebView.loadUrl(url);
//                                    mWebView.loadUrl("http://www.sport.es");
                                }
                            });

                        } catch (DbxException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                };
                Thread thread = new Thread(runnable);
                thread.start();
            }
        } catch (DbxException.Unauthorized unauthorized) {
            unauthorized.printStackTrace();
        } catch (DbxException e) {
            e.printStackTrace();
        }
    }

    private static int getNumberOfImages(List<DbxFileInfo> files){
        if (files == null || files.isEmpty()) {
            return 0;
        }
        int numberOfImages = 0;
        for(DbxFileInfo file:files) {
            String name = file.path.getName();
            if (isImage(name)) {
                numberOfImages++;
            }
        }
        return numberOfImages;
    }

    private static boolean isImage(String name){
        for(String format:supportedFormats){
            if(name.contains(format)){
                return true;
            }
        }
        return false;
    }

    private static List<DbxFileInfo> getImages(List<DbxFileInfo> files) {
        List<DbxFileInfo> dbxFileInfoList = new ArrayList<>();
        if (files == null || files.isEmpty()) {
            return dbxFileInfoList;
        }

        for(DbxFileInfo file:files) {
            String name = file.path.getName();
            if (isImage(name)) {
                dbxFileInfoList.add(file);
            }
        }
        return dbxFileInfoList;
    }

    private void getImages(DbxPath path) {
        final DbxFileSystem fileSystem;
        try {
            fileSystem = DbxFileSystem.forAccount(mDbxAcctMgr.getLinkedAccount());

        final List<DbxFileInfo> files = fileSystem.listFolder(path);
                int numberOfImages = getNumberOfImages(files);
                if (numberOfImages == 0 ) {
                    Toast.makeText(getApplicationContext(), "This folder doesn't contain any image.", Toast.LENGTH_SHORT).show();
                    return;
                } else {
                    Toast.makeText(getApplicationContext(), "This folder contains " + numberOfImages + " images", Toast.LENGTH_SHORT).show();

                    Runnable runnable = new Runnable() {
                        @Override
                        public void run() {
                            File root = Environment.getExternalStorageDirectory();

                            for(DbxFileInfo file: files) {
                                String name = file.path.getName();
                                if(isImage(name)){
                                    try {
                                        FileOutputStream f = new FileOutputStream(new File(root, name));
                                        DbxFile dbxFile = fileSystem.open(file.path);
                                        InputStream in = dbxFile.getReadStream();

                                        byte[] buffer = new byte[1024];
                                        int len1;
                                        while ((len1 = in.read(buffer)) > 0) {
                                            f.write(buffer, 0, len1);
                                        }
                                        f.close();

                                    } catch (FileNotFoundException e) {
                                        e.printStackTrace();
                                    } catch (DbxException e) {
                                        e.printStackTrace();
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        }
                    };
                    Thread thread = new Thread(runnable);
                    thread.start();
                }
        } catch (DbxException.Unauthorized unauthorized) {
            unauthorized.printStackTrace();
        } catch (DbxException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public class AppWebViewClients extends WebViewClient {

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            // TODO Auto-generated method stub
            view.loadUrl(url);
            return true;
        }

        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            super.onReceivedError(view, errorCode, description, failingUrl);
        }
    }
}
