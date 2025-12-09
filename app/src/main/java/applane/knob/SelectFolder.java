package applane.knob;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SelectFolder extends AppCompatActivity
{
    static String ALL_CARDS = "All Cards";
    private List<String> cardPaths;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_folder);

        cardPaths =  getCards();

        ListView listView = findViewById(R.id.listView);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, cardPaths);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            Intent intent = new Intent();
            intent.putExtra("card", cardPaths.get(position).equals(ALL_CARDS) ? "" : cardPaths.get(position));
            setResult(Activity.RESULT_OK, intent);
            finish();
        });
    }

    @NonNull
    public List<String> getCards() {
        List<String> externalPaths = new ArrayList<>();

        File[] allExternalFilesDirs = getExternalFilesDirs(null);
        for(File filesDir : allExternalFilesDirs) {
            if(filesDir != null) {
                int nameSubPos = filesDir.getAbsolutePath().toLowerCase().lastIndexOf("/android/data");
                if(nameSubPos > 0) {
                    String filesDirName = filesDir.getAbsolutePath().substring(0, nameSubPos);
                    externalPaths.add(filesDirName);
                }
            }
        }

        externalPaths.add(ALL_CARDS);
        return externalPaths;
    }

    public void Done(View v)
    {
        finish();
    }

}