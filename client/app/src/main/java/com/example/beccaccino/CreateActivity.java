package com.example.beccaccino;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.google.protobuf.InvalidProtocolBufferException;
import com.rabbitmq.client.*;
import org.w3c.dom.Text;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

public class CreateActivity extends AppCompatActivity {
    private List<TextView> usernames = new ArrayList<>();
    private final String todoQueueLobbies = "todoQueueLobbies";
    private final String resultsQueueLobbies = "resultsQueueLobbies";
    private final String todoQueueGames = "todoQueueGames";
    private final String resultsQueueGames = "resultsQueueGames";
    private String matchID;
    private Channel channel;
    private Connection connection;
    private Lobby lobby;
    private Player myPlayer;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create);

        Intent intent = getIntent();

        try {
            lobby = Lobby.parseFrom(intent.getByteArrayExtra("lobby"));
            myPlayer = Player.parseFrom(intent.getByteArrayExtra("player"));
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }

        matchID = lobby.getId();
        TextView matchIDTW = findViewById(R.id.matchID);
        matchIDTW.setText(matchID);

        TextView me = findViewById(R.id.player1Name);
        me.setText(MainActivity.getUsername(this));

        ImageButton backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> backToMain(CreateActivity.this));

        final Button startMatch = findViewById(R.id.startMatch);
        startMatch.setOnClickListener(v -> startMatch());

        saveViewElements();

    }

    @Override
    protected void onStart() {
        super.onStart();
        updateUsernames(lobby);
        executorService.execute(() -> {
            try {
                connection = Utilities.createConnection();
                channel = connection.createChannel();
                Utilities.createQueue(channel, todoQueueLobbies, BuiltinExchangeType.DIRECT, todoQueueLobbies,
                        false, false, false, null, "");
                Utilities.createQueue(channel, resultsQueueLobbies, BuiltinExchangeType.FANOUT, resultsQueueLobbies+myPlayer.getId(),
                        false, false, false, null, "");
                Utilities.createQueue(channel, todoQueueGames, BuiltinExchangeType.DIRECT, todoQueueGames,
                        false, false, false, null, "");
                Utilities.createQueue(channel, resultsQueueGames, BuiltinExchangeType.DIRECT, resultsQueueGames,
                        false, false, false, null, "");

                channel.basicConsume(resultsQueueLobbies+myPlayer.getId(), new DefaultConsumer(channel) {
                    @Override
                    public void handleDelivery(String consumerTag, Envelope envelope,
                                               AMQP.BasicProperties properties, byte[] body) throws IOException {
                        Response response = Response.parseFrom(body);
                        switch (Response.parseFrom(body).getResponseCode()) {
                            case(200) -> {}
                            case(201) -> {
                                if (response.getRequestingPlayer().getId().equals(myPlayer.getId())) {
                                    System.out.println("Uscito da: " + Response.parseFrom(body).getLobby().getId());
                                    Intent myIntent = new Intent(CreateActivity.this, MainActivity.class);
                                    CreateActivity.this.startActivity(myIntent);
                                    overridePendingTransition(R.anim.fragment_fade_enter, R.anim.fragment_fade_exit);
                                } else if(isMyLobby(response.getLobby())) {
                                    updateUsernames(response.getLobby());
                                }
                            }
                            case(202) -> {
                                if (response.getRequestingPlayer().getId().equals(myPlayer.getId())) {
                                    System.out.println("Ho joinato: " + Response.parseFrom(body).getLobby().getId());
                                } else if(isMyLobby(response.getLobby())) {
                                    System.out.println("E' arrivato un nuovo client");
                                    System.out.println("La nuova lobby è: " + response.getLobby().getPlayersList());
                                    updateUsernames(response.getLobby());
                                } else {
                                    System.out.println("Partita non mia");
                                }
                            }
                            case (402) -> SingleToast.show(getApplicationContext(), "Impossibile unirsi", 3000);

                            case (405) -> SingleToast.show(getApplicationContext(), "Permesso negato", 3000);

                            case (406) -> SingleToast.show(getApplicationContext(), "Richiesta illegale", 3000);

                            case (407) -> SingleToast.show(getApplicationContext(), "Operazione fallita", 3000);

                            default -> throw new IllegalStateException();

                        }
                    }
                });
            } catch (IOException | TimeoutException e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        executorService.execute(() -> {
            try {
                connection.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        MusicManager.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        /*MUSIC*/
        if(getSharedPreferences("Settings", MODE_PRIVATE).getBoolean("music", false)){
            MusicManager.start(this,0);
        }
    }

    private boolean isMyLobby(Lobby lobby){
        return lobby.getPlayersList().stream().anyMatch(p -> p.getId().equals(myPlayer.getId()));
    }

    private void updateUsernames(Lobby lobby){
        runOnUiThread(() -> {
            List<Player> players = lobby.getPlayersList();
            for (int i = 0; i < 4; i++) {
                    if (players.size() > i) {
                        usernames.get(i).setText(players.get(i).getNickname());
                    } else {
                        usernames.get(i).setText(R.string.waiting_player);
                    }
                }
        });
    }

    private void backToMain(final Context context) {
        AlertDialog.Builder alert = new AlertDialog.Builder(context);
        alert.setMessage("Vuoi lasciare la Lobby?");
        alert.setPositiveButton("Esci", (dialog, whichButton) -> executorService.execute(() -> {
            try {
                Request leaveLobbyRequest = Request.newBuilder().setLobbyId(matchID)
                        .setLobbyMessage("leave")
                        .setRequestingPlayer(myPlayer)
                        .build();

                channel.basicPublish(todoQueueLobbies, "", null, leaveLobbyRequest.toByteArray());
            } catch (IOException e) {
               e.printStackTrace();
            }
        }));
        alert.show();
    }

    private void startMatch() {
        // TODO comunicazione con il server
        if(checkNumPlayer()) {
            Intent myIntent = new Intent(CreateActivity.this, GameActivity.class);
            CreateActivity.this.startActivity(myIntent);
        } else {
            SingleToast.show(getApplicationContext(), "Seleziona tutti i giocatori prima di iniziare", 3000);
        }
    }

    private boolean checkNumPlayer() {
        List<String> names = new ArrayList<>();
        SharedPreferences sh
                = getApplication().getSharedPreferences("Settings",
                Context.MODE_PRIVATE);
        names.add(sh.getString("player1", "Paolo"));
        names.add(sh.getString("player2", "Scegli"));
        names.add(sh.getString("player3", "Scegli"));
        names.add(sh.getString("player4", "Scegli"));
        return !names.contains("Scegli");
    }

    private void saveViewElements() {
        TextView player1Name = findViewById(R.id.player1Name);
        TextView player2Name = findViewById(R.id.player2Name);
        TextView player3Name = findViewById(R.id.player3Name);
        TextView player4Name = findViewById(R.id.player4Name);
        usernames.add(player1Name);
        usernames.add(player2Name);
        usernames.add(player3Name);
        usernames.add(player4Name);
    }

}
