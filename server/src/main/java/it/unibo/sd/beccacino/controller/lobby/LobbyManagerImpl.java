package it.unibo.sd.beccacino.controller.lobby;

import it.unibo.sd.beccacino.*;
import it.unibo.sd.beccacino.controller.game.GameStub;
import org.bson.BsonValue;
import org.bson.Document;

import java.util.List;
import java.util.Objects;

public class LobbyManagerImpl implements LobbyManager {
    private static final int ROOM_CAPACITY = 4;
    private static final int TARGET_SCORE = 31;
    private final DBManager dbManager;
    private final LobbiesStub lobbiesStub;
    private final GameStub gameStub;

    public LobbyManagerImpl(LobbiesStub lobbiesStub, GameStub gameStub) {
        this.dbManager = new DBManager();
        this.lobbiesStub = lobbiesStub;
        this.gameStub = gameStub;
    }

    @Override
    public void handleRequest(Request request) {
        switch (request.getLobbyMessage()) {
            case ("create") -> this.createLobbyRequestHandler(request);
            case ("join") -> this.joinLobbyRequestHandler(request);
            case ("leave") -> this.leaveLobbyRequestHandler(request);
            case("start") -> {
                System.out.println("Start game handler");
                this.startGameLobbyRequestHandler(request);
            }
            default -> throw new RuntimeException(); // TODO: Log illegal request received.
        }
    }

    private void createLobbyRequestHandler(Request createLobbyRequest) {
        System.out.println("Request Create Lobby: "+ createLobbyRequest);
        Player requestingPlayer = createLobbyRequest.getRequestingPlayer();
        System.out.println("Request Player Create Lobby: "+ requestingPlayer);

        String roomID = this.createNewLobby(requestingPlayer).asObjectId().getValue().toString();

        System.out.println("Request new Lobby: "+ roomID);

        System.out.println("Lobby effectively exist" + this.getLobby(roomID));

        if (!Objects.equals(roomID, "")) {
            this.lobbiesStub.sendLobbyResponse(this.getLobby(roomID), ResponseCode.CREATE_OK, requestingPlayer);
        } else {
            this.lobbiesStub.sendLobbyResponse(null, ResponseCode.CREATE_ERROR, requestingPlayer);
        }
    }

    private void joinLobbyRequestHandler(Request joinLobbyRequest) {
        System.out.println("Request Join Lobby: "+ joinLobbyRequest);
        String joinLobbyId = joinLobbyRequest.getLobbyId();
        System.out.println("Request ID Join Lobby: "+ joinLobbyId);
        Player playerJoined = joinLobbyRequest.getRequestingPlayer();
        System.out.println("Request Player Create Lobby: "+ playerJoined);
        if (this.doesLobbyExist(joinLobbyId) && this.getLobbySize(joinLobbyId) <= ROOM_CAPACITY) {
            System.out.println("La lobby esiste e non è piena");
            boolean statusRequest = this.dbManager.updateLobbyPlayers(playerJoined, joinLobbyId);
            System.out.println("Dopo aver aggiornato la lobby di player ti dico: ..." + statusRequest);
            Lobby lobbyUpdated = this.getLobby(joinLobbyId);
            if (statusRequest) {
                System.out.println("Join LObby OK");
                this.lobbiesStub.sendLobbyResponse(lobbyUpdated, ResponseCode.JOIN, playerJoined);
            } else {
                System.out.println("Join LObby ERROR");
                this.lobbiesStub.sendLobbyResponse(lobbyUpdated, ResponseCode.JOIN_ERROR, playerJoined);
            }
        } else {
            // TODO could be also a join error because the lobby is full.
            System.out.println("Join LObby FULL");
            this.lobbiesStub.sendLobbyResponse(null, ResponseCode.CREATE_ERROR, playerJoined);
        }
    }

    private void leaveLobbyRequestHandler(Request leaveLobbyRequest) {
        Player removedPlayer = leaveLobbyRequest.getRequestingPlayer();
        String lobbyId = leaveLobbyRequest.getLobbyId();

        boolean statusRequest = this.dbManager.removeLobbyPlayer(removedPlayer, lobbyId);
        System.out.println("Lobby updated with statusRequest: " + statusRequest);
        Lobby lobbyUpdated = this.getLobby(lobbyId);
        System.out.println("Lobby updated within: " + lobbyUpdated);

        if (statusRequest) {
            System.out.println("Lobby updated WELL");
            this.lobbiesStub.sendLobbyResponse(lobbyUpdated, ResponseCode.LEAVE, removedPlayer);
            if (this.getLobbySize(lobbyId) == 0) {
                this.deleteLobby(lobbyId);
            }
        } else {
            System.out.println("Lobby updated smells like shit");
            this.lobbiesStub.sendLobbyResponse(lobbyUpdated, ResponseCode.LEAVE_ERROR, removedPlayer);
        }
    }

    private void startGameLobbyRequestHandler(Request request) {
        Lobby lobby = this.getLobby(request.getLobbyId());

        String gameId = this.gameStub.startNewGame(GameRequest.newBuilder()
                                                .setRequestType("start")
                                                .setRequestingPlayer(request.getRequestingPlayer())
                                                .setLobby(lobby)
                                                .build());

        System.out.println("GameID di start: " + gameId + "lobby di start: "+ lobby);

        switch (gameId) {
            case "error" -> {
                System.out.println("Errore");
                this.lobbiesStub.sendLobbyResponse(lobby, ResponseCode.START_ERROR, request.getRequestingPlayer());
            }
            case "permission-denied" -> {
                System.out.println("Permesso negato");
                this.lobbiesStub.sendLobbyResponse(lobby, ResponseCode.PERMISSION_DENIED, request.getRequestingPlayer());
            }
            case "illegal" -> {
                System.out.println("Illegal request");
                this.lobbiesStub.sendLobbyResponse(lobby, ResponseCode.ILLEGAL_REQUEST, request.getRequestingPlayer());
            }
            default -> {System.out.println("Start correttamente");
                this.lobbiesStub.sendGameStartLobbyResponse(lobby, request.getRequestingPlayer(), gameId);
            }
        }
    }

    private Lobby getLobby(String id) {
        return this.dbManager.getLobbyById(id);
    }

    private void deleteLobby(String id) {
        this.dbManager.removeDocument("_id", id, "lobbies");
    }

    private int getLobbySize(String id) {
        return this.getLobby(id).getPlayersList().size();
    }

    private boolean doesLobbyExist(String id) {
        return this.dbManager.getLobbyById(id) != null;
    }

    private BsonValue createNewLobby(Player requestingPlayer) {
        Document newLobby = new Document("room_capacity", ROOM_CAPACITY)
                .append("target_score", TARGET_SCORE)
                .append("players", List.of(new Document("_id", requestingPlayer.getId())
                        .append("nickname", requestingPlayer.getNickname())));
        return this.dbManager.insertDocument(newLobby, "lobbies");
    }
}
