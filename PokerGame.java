/*
 *  project: pokerGame
 *     file: src\main\java\org\csc478\pokerGame\models\PokerGame.java
 *  created: 2018-11-09 13:09:08
 *       by: Gino Canessa
 * modified: 2018-11-12
 *       by: Gino Canessa
 *
 *  summary: Internal representation of a single game of poker
 */

package org.csc478.pokerGame.models;

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;

import javax.activity.InvalidActivityException;

import org.csc478.pokerGame.models.GameAction.GameActionTypes;

public class PokerGame {

  //#region Public Constants . . .

  public static final int GameRoundStateInvalid = 0;
  public static final int GameRoundStateWaitingForPlayer = 1;
  public static final int GameRoundStateDealing = 2;
  public static final int GameRoundStateComputerPlayer = 3;
  public static final int GameRoundStateGameOver = 4;

  //#endregion Public Constants . . .

  //#region Public Variables . . .

  //#endregion Public Variables . . .

  //#region Object Variables . . .

  /** Unique identifier for this game */
  private final UUID _pokerGameId;

  /** Number of actions take in this game so far */
  private int _gameActionCount;

  /** List of actions performed in this game */
  private List<GameAction> _gameActions;

  /** Array of players in this game */
  private PokerPlayer _players[];

  /** Array of flags for if a player is still in the game */
  private boolean _playerActiveFlags[];
  
  /** Number of players currently active in this hand */
  private int _handActivePlayerCount;

  /** Number of Calls in a row for this round */
  private int _consecutiveCallCount;

  /** Number of players showing their cards in this round */
  private int _playerShowingCount;

  /** Number of players in game (to avoid frequently accessing list size) */
  private int _playerCount;

  /** Array of player hands in this game */
  private PlayerHand _playerHands[];

  /** The ante for this table (in dollars), can be zero */
  private final int _ante;

  /** The low stake limit for this table (in dollars) */
  private final int _lowStake;

  /** The high stake limit for this table (in dollars) */
  private final int _highStake;

  /** Current round of the game */
  private int _currentRound;

  /** State of the current round of play */
  private int _currentRoundState;

  /** Deck of cards in use */
  private CardDeck _gameDeck;
  public String[] gameDeck = new String[52];


  /** Number of cards from the deck which have been used */
  private int _numberOfCardsUsed;

  /** Number of cards which have been dealt to each player */
  private int _cardsDealtPerPlayer = 0;

  /** Index of the first player that got to take action this round */
  private int _firstPlayerThisRound = 0;

  /** Number of players which have had a turn this round */
  private int _playerTurnsThisRound;

  /** Amount (in dollars) of the current pot */
  private int _potValue;

  /** Minumum bet amount to stay in the game */
  private int _currentBetToStayIn;

  private int _playerBetTotalsThisRound[];

  /** Queue for holding requested actions */
  private Queue<GameAction> _requestedActions;

  private Thread _gameProcessThread;

  //#endregion Object Variables . . .

  //#region Accessors and Mutators . . .

  /**
   * Gets the Ante (in dollars) for this game
   * @return The ante (in dollars) for thi game
   */
  public int getAnte() { return _ante; }

  /**
   * Gets the Low Stake Limit (in dollars) for this game
   * @return The Low Stake Limit (in dollars) for this game
   */
  public int getLowStake() { return _lowStake; }

  /**
   * Gets the High Stake Limit (in dollars) for this game
   * @return The High Stake Limit (in dollars) for this game
   */
  public int getHighStake() { return _highStake; }

  /**
   * Gets the Number of Players in this game
   * @return The Number of Players in this game
   */
  public int getNumberOfPlayers() { return _playerCount; }

  /**
   * Get the current round number of the game
   * @return The current round number of the game
   */
  public int getRoundNumber() { return _currentRound; }

  /**
   * Get the state of the current round of play
   * @return The state of the current round of play
   */
  public int getRoundState() { return _currentRoundState; }
  /**
   * Current active players in the hand to determine who needs to bet,raise,fold, etc.
   * @return The array, in position of the active status of players in the game.
   */ 
  public boolean[] getActivePlayers() {return _playerActiveFlags;} 

  /**
   * Get the current total bet required to stay in the game
   * @return The TOTAL amount of bet required to stay in the game
   */
  public int getCurrentMinumumTotalBet() { return _currentBetToStayIn; }

  /**
   * Gets the current minimum bet required by a given player to stay in the game
   * @param playerIndex Game Player index for the player
   * @return Minumum bet required, in dollars
   */
  public int getCurrentMinumumBetForPlayerIndex(int playerIndex) { 
    return (_currentBetToStayIn > _playerBetTotalsThisRound[playerIndex]) 
      ? _currentBetToStayIn - _playerBetTotalsThisRound[playerIndex]
      : 0 ;
  }

  /**
   * Gets the face up scores of all other players, excluding the one passed
   * @param gamePlayerIndex Game player index for the player
   * @return Array of scores
   */
  public int[] getOtherPlayerFaceUpScores(int gamePlayerIndex) {
    int faceUpScores[] = new int[_playerCount - 1];

    int scoresAdded = 0;
    for (int playerIndex = 0; playerIndex < _playerCount; playerIndex++)
    {
      if (playerIndex != gamePlayerIndex)
      {
        faceUpScores[scoresAdded++] = _playerHands[playerIndex].getScoreFaceUp();
      }
    }

    return faceUpScores;
  }

  /**
   * Gets the face up scores of all players
   * @return Array of scores, in game player order
   */
  public int[] getFaceUpScores() {
    int faceUpScores[] = new int[_playerCount];

    for (int playerIndex = 0; playerIndex < _playerCount; playerIndex++)
    {
      faceUpScores[playerIndex] = _playerHands[playerIndex].getScoreFaceUp();
    }

    return faceUpScores;
  }

  public int getClosestValidBet(int requestedBet) {

    int validAmount = 0;

    // **** act depending on current round ****

    switch (_currentRound)
    {
      case 1:
        // **** round 1 starts at low stake, but can be any amount ****

        validAmount = _lowStake > requestedBet ? _lowStake : requestedBet;
      break;

      case 2:

        // **** bet is low stake unless there is a showing pair ****

        validAmount = _lowStake;

        // **** traverse players looking for pair ****

        for (int handIndex = 0; handIndex < _playerCount; handIndex++)
        {
          if (_playerHands[handIndex].getHandTypeFaceUp() == PokerScorer.ScoreTypeOnePair)
          {
            // **** check for multiples of low/high ****

            if (_currentBetToStayIn % _highStake == 0)
            {
              // **** multiples of high stake ****

              validAmount = (requestedBet / _highStake) * _highStake;
            }
            else
            {
              // **** multiples of low stake ****

              validAmount = (requestedBet / _lowStake) * _lowStake;
            }
          }
        }

      break;

      case 3:

        // **** bet is high stake ****

        validAmount = _highStake;

      break;

      case 4:

        // **** any bet is valid ****

        validAmount = requestedBet;

      break;

      case 5:

        // **** any bet is valid ****

        validAmount = requestedBet;

      break;
    }

    // **** make sure it's at least the current bet ****

    if (validAmount < _currentBetToStayIn)
    {
      validAmount = _currentBetToStayIn;
    }

    // **** return our amount ****

    return validAmount;
  }

  //#endregion Accessors and Mutators . . .

  //#region Constructors . . .

  /**
   * Create a game of poker with the specified ante and stakes
   * @param ante The ante for this game (0 or more)
   * @param lowStake The low stake for this game (1 or more, must be lower than highStake)
   * @param highStake The high stake for this game (2 or more, must be higher than lowStake)
   */
  public PokerGame(
    final int ante,
    final int lowStake,
    final int highStake
    )
  {
    // **** sanity checks ***

    if (ante < 0)
    {
      throw new InvalidParameterException(String.format("Invalid table ante: %d", ante));
    }

    if (lowStake < 1)
    {
      throw new InvalidParameterException(String.format("Invalid table lowStake: %d", lowStake));
    }

    if (highStake < 2)
    {
      throw new InvalidParameterException(String.format("Invalid table highStake: %d", highStake));
    }

    if (lowStake <= highStake)
    {
      throw new InvalidParameterException(
        String.format("Invalid table stakes, low: %d must be smaller than high: %d", lowStake, highStake));
    }

    _requestedActions = new LinkedList<GameAction>();

    // **** generate a UUID ****

    _pokerGameId = UUID.randomUUID();

    _gameActionCount = 0;
    _gameActions = new ArrayList<GameAction>();

    // **** set basic game information ****

    _ante = ante;
    _lowStake = lowStake;
    _highStake = highStake;
    
    // **** no players have been added yet ****

    _players = new PokerPlayer[7];
    _playerHands = new PlayerHand[7];
    _playerCount = 0;
    _playerTurnsThisRound = 0;

    _currentRound = 0;

    // **** set up our cards ****

    _gameDeck = new CardDeck();
    _numberOfCardsUsed = 0;

    _potValue = 0;

    _gameProcessThread = null;

  }
  //#endregion Constructors . . .

  //#region Public Interface . . .

  /**
   * Add a player to the game
   * @param player Player to add to this game
   * @return Number of players in the game
   * @throws InvalidActivityException Attempt to exceed the allowed number of players
   */
  public int AddPlayer(PokerPlayer player) throws InvalidActivityException
  {
    // **** sanity checks ****

    if (_playerCount > 6)
    {
      throw new InvalidActivityException(String.format("Cannot add another player to the game."));
    }

    // **** append this player ****

    _players[_playerCount] = player;
    _playerHands[_playerCount] = new PlayerHand(player.getPlayerId(), _playerCount);
    player.setGamePlayerIndex(_playerCount);

    // **** increment our number of players ****

    _playerCount++;

    // **** return the number of players ****

    return _playerCount;
  }

  /**
   * Start this game of poker
   */
  public void StartGame()
  {
    // **** start with round 0 ****

    _currentRound = 0;
    _cardsDealtPerPlayer = 0;
    _numberOfCardsUsed = 0;

    _playerTurnsThisRound = 0;

    _gameActionCount = 0;
    _gameActions = new ArrayList<GameAction>();

    _playerShowingCount = 0;
    _potValue = 0;

    // **** all players are assumed active ****

    _playerActiveFlags = new boolean[_playerCount];
    for (int playerIndex = 0; playerIndex < _playerCount; playerIndex++)
    {
      _playerActiveFlags[playerIndex] = true;
    }

    _handActivePlayerCount = _playerCount;
    _consecutiveCallCount = 0;
    _playerShowingCount = 0;

    // **** game state is not valid yet ****

    _currentRoundState = GameRoundStateInvalid;

    // **** request that our deck gets shuffled ****

    RequestShuffleDeck();

    // **** make sure a game thread is running ****

    if (_gameProcessThread != null)
    {
      Runnable procRunnable = () -> {GameThreadProc();};
      _gameProcessThread = new Thread(procRunnable);
      _gameProcessThread.start();
    }
    else
    {
      if (!_gameProcessThread.isAlive())
      {
        _gameProcessThread.start();
      }
    }

  }

  /**
   * Function to process game actions
   */
  private void GameThreadProc()
  {
    while (!Thread.currentThread().isInterrupted())
    {
      // **** check for no actions in our queue ****

      if (_requestedActions.size() == 0)
      {
        try {
          Thread.sleep(100);
        } catch (Exception e) {
          // **** ignore the exception for now ****
        }

        // **** go to next loop ****

        continue;
      }

      // **** grab our next action ****

      GameAction action = _requestedActions.remove();

      // **** process this action ****

      ProcessAction(action);
    }
  }


  /**
   * Process an action in this game
   * @param action Action to process
   */
  private void ProcessAction(GameAction action)
  {
    // **** act depending on type ****

    switch (action.getActionType())
    {
      case GameAction.GameActionTypeShuffleDeck:
      {
        // **** shuffle the deck ****

        _gameDeck.ShuffleDeck();

        // **** check to see if we need to process ante payment ****

        if (_ante != 0)
        {
          // **** need to wait on ante ****

          RequestAnte();
        }
        else
        {
          // **** need to burn first card ****

          RequestBurnCard();
        }
      }
      break;

      case GameAction.GameActionTypeRequestAnte:
      {
        // **** process ante payment from computer players ****

        for (int playerIndex = 0; playerIndex < _playerCount; playerIndex++)
        {
          int playerAmount = _players[playerIndex].getDollars();

          // **** make sure they can pay ****

          if (playerAmount < _ante)
          {
            // **** player will not be active this hand ****

            _playerActiveFlags[playerIndex] = false;
            _handActivePlayerCount--;
          }

          // **** take their ante payment ****

          playerAmount -= _ante;

          // **** update player amount ****

          _players[playerIndex].setDollars(playerAmount);

          // **** update the pot ****

          _potValue += _ante;
        }

        // **** request next state ****

        RequestBurnCard();
      }
      break;

      case GameAction.GameActionTypeBurnCard:
      {
        // **** remove next card from shuffled deck ****

        _gameDeck.CardAt(_numberOfCardsUsed++).setCardState(PlayingCard.CardStateOutOfPlay);

        // **** set state ****

        _currentRoundState = GameRoundStateDealing;

        // **** next state is dealing face down ****

        RequestDealFaceDown();
      }
      break;

      case GameAction.GameActionTypeDealFaceDown:
      {
        // **** deal a face down card to each player ****

        for (int playerIndex = 0; playerIndex < _playerCount; playerIndex++)
        {
          // **** deal this player the next card ****

          _playerHands[playerIndex].AddCardToHand(_gameDeck.CardAt(_numberOfCardsUsed));

          // **** flag that this card has been dealt face down ****

          _gameDeck.CardAt(_numberOfCardsUsed).setCardState(PlayingCard.CardStateFaceDown);

          // **** this card has been used ****

          _numberOfCardsUsed++;
        }

        // **** another card has been dealt to each player ****

        _cardsDealtPerPlayer++;

        // **** act depending on how many cards have been dealt ****

        RequestActionAfterDeal();
      }
      break;

      case GameAction.GameActionTypeDealFaceUp:
      {
        // **** deal a face up card to each player ****

        for (int playerIndex = 0; playerIndex < _playerCount; playerIndex++)
        {
          // **** deal this player the next card ****

          _playerHands[playerIndex].AddCardToHand(_gameDeck.CardAt(_numberOfCardsUsed));

          // **** flag that this card has been dealt face down ****

          _gameDeck.CardAt(_numberOfCardsUsed).setCardState(PlayingCard.CardStateFaceUp);

          // **** this card has been used ****

          _numberOfCardsUsed++;
        }

        // **** another card has been dealt to each player ****

        _cardsDealtPerPlayer++;

        // **** act depending on how many cards have been dealt ****

        RequestActionAfterDeal();
      }
      break;

      case GameAction.GameActionTypeRequestPlayerAction:
      {
        // **** determine which player's turn it is ****
        
        int playerNumber = (_firstPlayerThisRound + _playerTurnsThisRound++) % _playerCount;

        // **** check to see if this player is still in this hand ****

        while (_playerActiveFlags[playerNumber] == false)
        {
          // **** move to next player ****

          playerNumber = (_firstPlayerThisRound + _playerTurnsThisRound++) % _playerCount;
        }

        // **** ask the player for an action ****

        if (_players[playerNumber].getIsComputer() == true)
        {
          // **** set game state ****

          _currentRound = GameRoundStateComputerPlayer;

          // **** ask the player for an action ****

          _players[playerNumber].RequestActionForGame(_playerHands[playerNumber], this);
        }
        else
        {
          // **** wait for human player ****

          _currentRoundState = GameRoundStateWaitingForPlayer;
        }
      }
      break;

      case GameAction.GameActionTypeRaise:
      {
        // **** grab the player index (for sanity) ****
        
        int playerIndex = action.getGamePlayerIndex();

        // **** make sure the player has enough money to raise ****

        int playerAmount = _players[playerIndex].getDollars();

        int raiseDifference = (_currentBetToStayIn - _playerBetTotalsThisRound[playerIndex]) + action.getAmount();

        // **** make sure they can pay ****

        if (playerAmount < raiseDifference)
        {
          // **** player will not be active this hand ****
    
          _playerActiveFlags[playerIndex] = false;
          _handActivePlayerCount--;

          // **** reset to empty hand ****

          _playerHands[playerIndex] = new PlayerHand(_players[playerIndex].getPlayerId(), playerIndex);
        }
        else
        {
          // **** take their payment ****

          playerAmount -= raiseDifference;

          // **** update player amount ****

          _players[playerIndex].setDollars(playerAmount);

          // **** update the player's bet state ****

          _playerBetTotalsThisRound[playerIndex] += raiseDifference;

          // **** update the pot ****

          _potValue += raiseDifference;
        }

        // **** reset call count, raising counts as the first call ****

        _consecutiveCallCount = 1;

        // **** determine next action in the game ****

        RequestActionAfterPlayerAction();
      }
      break;

      case GameAction.GameActionTypeCall:
      {
        // **** grab the player index (for sanity) ****
        
        int playerIndex = action.getGamePlayerIndex();

        // **** make sure the player has enough money to call ****

        int playerAmount = _players[playerIndex].getDollars();

        int callDifference = _currentBetToStayIn - _playerBetTotalsThisRound[playerIndex];

        // **** make sure they can pay ****

        if (playerAmount < callDifference)
        {
          // **** player will not be active this hand ****
    
          _playerActiveFlags[playerIndex] = false;
          _handActivePlayerCount--;

          // **** reset to empty hand ****

          _playerHands[playerIndex] = new PlayerHand(_players[playerIndex].getPlayerId(), playerIndex);
        }
        else
        {
          // **** take their payment ****

          playerAmount -= callDifference;

          // **** update player amount ****

          _players[playerIndex].setDollars(playerAmount);

          // **** update the player's bet state ****

          _playerBetTotalsThisRound[playerIndex] += callDifference;

          // **** update the pot ****

          _potValue += callDifference;
        }
        
        // **** flag that we called ****

        _consecutiveCallCount++;

        // **** determine next action in the game ****

        RequestActionAfterPlayerAction();
      }
      break;

      case GameAction.GameActionTypeFold:
      case GameAction.GameActionTypeMuck:
      {
        // **** grab the player index (for sanity) ****

        int playerIndex = action.getGamePlayerIndex();

        // **** player will not be active this hand ****
  
        _playerActiveFlags[playerIndex] = false;
        _handActivePlayerCount--;

        // **** reset to empty hand ****

        _playerHands[playerIndex] = new PlayerHand(_players[playerIndex].getPlayerId(), playerIndex);

        // **** determine next action in the game ****

        RequestActionAfterPlayerAction();
      }
      break;

      case GameAction.GameActionTypeShowCards:
      {
        // **** flag we are showing ****

        _playerShowingCount++;

        // **** determine next action in the game ****

        RequestActionAfterPlayerAction();
      }
      break;

      case GameAction.GameActionTypeEndGame:
      {
        int winnerIndex = 0;
        int winningScore = 0;
  
        // **** score the game ****
  
        for (int playerIndex = 0; playerIndex < _playerCount; playerIndex++)
        {
          // **** check for new high score - ties go to closer to dealer ****
  
          if (_playerHands[playerIndex].getScoreTotal() > winningScore)
          {
            winnerIndex = playerIndex;
            winningScore = _playerHands[playerIndex].getScoreTotal();
          }
        }
  
        // **** notifiy our winner ****
  
        _players[winnerIndex].SetWinner(_playerHands[winnerIndex].getHandTypeTotal(), _potValue);
  
        // **** add the pot value to the player total ****
  
        int playerDollars = _players[winnerIndex].getDollars();
        playerDollars += _potValue;
        _players[winnerIndex].setDollars(playerDollars);

        // **** set state ****

        _currentRoundState = GameRoundStateGameOver;
      }
      break;
    }

    // **** keep track of actions ****
    
    _gameActions.add(action);
  }

  /**
   * Request the next action after dealing a card
   */
  private void RequestActionAfterPlayerAction()
  {
    // **** check for a single player left or all remaining players showing ****

    if ((_handActivePlayerCount == 1) || (_playerShowingCount == _handActivePlayerCount))
    {
      RequestEndGame();

      // **** done ****

      return;
    }

    // **** check our consecutive call count (round is over when all have called) ****

    if (_consecutiveCallCount == _handActivePlayerCount)
    {
      // **** act depending on round we are in ****

      if ((_currentRound == 1) ||
          (_currentRound == 2) ||
          (_currentRound == 3))
      {
        // **** next action is to deal face up ****

        RequestDealFaceUp();

        // **** done ****

        return;
      }

      if (_currentRound == 4)
      {
        // **** next action is to deal face down ****

        RequestDealFaceDown();

        // **** done ****

        return;
      }

      // **** final round ****

      AdvanceRound(false);

      // **** done ****

      return;
    }

    // **** need to get next player action ****

    RequestPlayerAction();
  }

  /**
   * Chose the first player for a round
   * @return Player Index of the first player for this round
   */
  private int ChooseFirstPlayer()
  {
    int playerNumber = 0;

    // **** act depending on round ****

    if (_currentRound == 1)
    {
      int lowScore = PokerScorer.InvalidHighScore;

      // **** traverse players looking for lowest score ****

      for (int handIndex = 0; handIndex < _playerCount; handIndex++)
      {
        int handScore = _playerHands[handIndex].getScoreFaceUp();

        if (handScore < lowScore)
        {
          lowScore = handScore;
          playerNumber = handIndex;
        }
      }

      // **** done ****

      return playerNumber;
    }

    // **** use highest exposed hand ****

    int highScore = 0;

    // **** traverse players looking for highest score ****

    for (int handIndex = 0; handIndex < _playerCount; handIndex++)
    {
      int handScore = _playerHands[handIndex].getScoreFaceUp();

      if (handScore > highScore)
      {
        highScore = handScore;
        playerNumber = handIndex;
      }
    }

    // **** 

    return playerNumber;
  }


  /**
   * Advance to the next round of the game internally
   * @param isFirstRound True if this is the first round
   */
  private void AdvanceRound(boolean isFirstRound)
  {
    // **** check for first round ****

    if (isFirstRound)
    {
      _currentRound = 1;
    }
    else
    {
      _currentRound++;
    }

    // **** figure out player turn info ****

    _firstPlayerThisRound = ChooseFirstPlayer();
    _playerTurnsThisRound = 0;

    // **** no bets have been made this round ****

    _playerBetTotalsThisRound = new int[_playerCount];

    // **** configure betting for this round ****

    _currentBetToStayIn = getClosestValidBet(0);

    // **** we need to start player actions ****

    RequestPlayerAction();
  }


  /**
   * Request the next action after dealing a card
   */
  private void RequestActionAfterDeal()
  {
    // **** act depending on number of cards dealt ****

    switch (_cardsDealtPerPlayer)
    {
      case 1:
        // **** next action is a face down deal ****

        RequestDealFaceDown();

      break;

      case 2:
        // **** next action is a face up deal ***

        RequestDealFaceUp();

      break;

      case 3:
        // **** we now move into round one ****

        AdvanceRound(true);

      break;

      case 4:
      case 5:
      case 6:
      case 7:

        // **** move into the next round ****

        AdvanceRound(false);

      break;
    }
  }

  /**
   * Enqueue a request for processing
   * @param action Action being requested
   */
  private void RequestAction(GameAction action)
  {
    _requestedActions.add(action);
  }

    /** Request that the game ends */
    private void RequestEndGame()
    {
      RequestAction(new GameAction(null, -1, _pokerGameId, GameAction.GameActionTypeEndGame, 0, _currentRound, _gameActionCount++));
    }
  
  /** Request that a player takes action */
  private void RequestPlayerAction()
  {
    RequestAction(new GameAction(null, -1, _pokerGameId, GameAction.GameActionTypeRequestPlayerAction, 0, _currentRound, _gameActionCount++));
  }
  
  /** Request that the dealer deals a face-up card to each player */
  public void RequestDealFaceUp()
  {
    RequestAction(new GameAction(null, -1, _pokerGameId, GameAction.GameActionTypeDealFaceUp, 1, _currentRound, _gameActionCount++));
    
    
    
    
  }

  /** Request that the dealer deals a face-down card to each player */
  public void RequestDealFaceDown()
  {
    RequestAction(new GameAction(null, -1, _pokerGameId, GameAction.GameActionTypeDealFaceDown, 1, _currentRound, _gameActionCount++));
    

    
    
  }

  /** Request that the game shuffles the deck */

  public void RequestShuffleDeck()
  {  
	  
    RequestAction(new GameAction(null, -1, _pokerGameId, GameAction.GameActionTypeShuffleDeck, 0, _currentRound, _gameActionCount++));
    
    _gameDeck.ShuffleDeck();
    	for(int i = 0; i<51; i++) {
    		//System.out.println("shuffling again");
    		String cardSuitName = "";
    		String cardRank="";	
		
			if(CardDeck.getCardSuit(i)==64) {
				cardSuitName = "Spades";
			}else if(CardDeck.getCardSuit(i)==48) {
				cardSuitName = "Diamonds";
			}else if(CardDeck.getCardSuit(i)==32) {
				cardSuitName = "Hearts";
			}else if(CardDeck.getCardSuit(i)==16) {
				cardSuitName = "Clubs";
			}else {System.out.println("INVALID SUIT");}
			
			if(CardDeck.getCardRank(i) > 1 && CardDeck.getCardRank(i) <11)
			{
				int tempCardRank = CardDeck.getCardRank(i);
				cardRank= Integer.toString(tempCardRank);
			}else if(CardDeck.getCardRank(i)==11) {
				cardRank = "Jack";
			}else if(CardDeck.getCardRank(i)==12) {
				cardRank = "Queen";
			}else if(CardDeck.getCardRank(i)==13) {
				cardRank = "King";
			}else if(CardDeck.getCardRank(i)==14) {
				cardRank = "Ace";
			}
			
			//build GUI CARD DECK
			String deckCard =  cardRank +" of " + cardSuitName;

			gameDeck[i]=deckCard;
			
			//System.out.println(gameDeck[i]);			
    		}
    
    
    
  }

  
  public String getGameCardsSuit(int cardNumber) {
	  String cardSuitName = "";
			if(CardDeck.getCardSuit(cardNumber)==64) {
				cardSuitName = "Spades";
			}else if(CardDeck.getCardSuit(cardNumber)==48) {
				cardSuitName = "Diamonds";
			}else if(CardDeck.getCardSuit(cardNumber)==32) {
				cardSuitName = "Hearts";
			}else if(CardDeck.getCardSuit(cardNumber)==16) {
				cardSuitName = "Clubs";
			}else {System.out.println("INVALID SUIT");}
			System.out.println(cardSuitName); 	
	  return cardSuitName;
	  
  }
  
  public String getGameCardsRank(int cardNumber) {
		String cardRank="";	
		if(CardDeck.getCardRank(cardNumber) > 1 && CardDeck.getCardRank(cardNumber) <11)
		{
			int tempCardRank = CardDeck.getCardRank(cardNumber);
			cardRank= Integer.toString(tempCardRank);
		}else if(CardDeck.getCardRank(cardNumber)==11) {
			cardRank = "Jack";
		}else if(CardDeck.getCardRank(cardNumber)==12) {
			cardRank = "Queen";
		}else if(CardDeck.getCardRank(cardNumber)==13) {
			cardRank = "King";
		}else if(CardDeck.getCardRank(cardNumber)==14) {
			cardRank = "Ace";
		}else {System.out.println("INVALID Rank");}
		System.out.println(cardRank);
	  return cardRank;
	  
  }
  public String getGameCards(int cardNumber) {
	  
	  //System.out.println(gameDeck[cardNumber]);			

	  return gameDeck[cardNumber];
	  
  }

  /** Request that all players pay the ante */
  private void RequestAnte()
  {
    RequestAction(new GameAction(null, -1, _pokerGameId, GameAction.GameActionTypeRequestAnte, _ante, _currentRound, _gameActionCount++));
  }

  /** Request that the dealer burns a card from the deck */
  private void RequestBurnCard()
  {
    RequestAction(new GameAction(null, -1, _pokerGameId, GameAction.GameActionTypeBurnCard, 1, _currentRound, _gameActionCount++));
  }

  /**
   * Place a player request to fold (only valid in rounds 1 through 5)
   * @param playerIndex Game Player Index of the player requesting this action
   */
  public void PlayerActionFold(int playerIndex)
  {
    RequestAction(
      new GameAction(
        _players[playerIndex].getPlayerId(), 
        playerIndex, 
        _pokerGameId, 
        GameAction.GameActionTypeFold, 
        0, 
        _currentRound, 
        _gameActionCount++));
  }

  /**
   * Place a player request to muck their cards (only valid in round 6)
   * @param playerIndex Game Player Index of the player requesting this action
   */
  public void PlayerActionMuck(int playerIndex)
  {
    RequestAction(
      new GameAction(
        _players[playerIndex].getPlayerId(), 
        playerIndex, 
        _pokerGameId, 
        GameAction.GameActionTypeMuck, 
        0, 
        _currentRound, 
        _gameActionCount++));
  }

  /**
   * Place a player request to bet with a raise
   * @param playerIndex Game Player Index of the player requesting this action
   * @param amount Amount the player wishes to raise
   */
  public void PlayerActionRaise(int playerIndex, int amount)
  {
    RequestAction(
      new GameAction(
        _players[playerIndex].getPlayerId(), 
        playerIndex, 
        _pokerGameId, 
        GameAction.GameActionTypeRaise, 
        amount, 
        _currentRound, 
        _gameActionCount++));
  }

  /**
   * Place a player request to call
   * @param playerIndex Game Player Index of the player requesting this action
   * @param amount Amount the player must pay in order to call
   */
  public void PlayerActionCall(int playerIndex, int amount)
  {
    RequestAction(
      new GameAction(
        _players[playerIndex].getPlayerId(), 
        playerIndex, 
        _pokerGameId, 
        GameAction.GameActionTypeCall, 
        amount, 
        _currentRound, 
        _gameActionCount++));
  }

  /**
   * Place a player request to show cards
   * @param playerIndex Game Player Index of the player requesting this action
   */
  public void PlayerActionShow(int playerIndex)
  {
    RequestAction(
      new GameAction(
        _players[playerIndex].getPlayerId(), 
        playerIndex, 
        _pokerGameId, 
        GameAction.GameActionTypeShowCards, 
        0, 
        _currentRound, 
        _gameActionCount++));
  }

  
  /**
   * Get the list of valid actions for the player, given the current game state
   * @return List of Valid GameActionTypes
   */
  public List<GameActionTypes> getValidPlayerActions()
  {
    List<GameActionTypes> validActions = new ArrayList<GameActionTypes>();

    // **** act depending on round ****

    switch (_currentRound)
    {
      case 1:
        validActions.add(GameActionTypes.Call);
        validActions.add(GameActionTypes.Fold);
        validActions.add(GameActionTypes.Raise);
      break;

      case 2:
        validActions.add(GameActionTypes.Call);
        validActions.add(GameActionTypes.Fold);

        // **** check to see if raise is valid ****
        
        for (int handIndex = 0; handIndex < _playerCount; handIndex++)
        {
          // **** check for a face-up pair ****

          if (_playerHands[handIndex].getHandTypeFaceUp() == PokerScorer.ScoreTypeOnePair)
          {
            // **** raising is valid ****

            validActions.add(GameActionTypes.Raise);

            // **** stop looking ****

            break;
          }
        }

      break;

      case 3:
        validActions.add(GameActionTypes.Call);
        validActions.add(GameActionTypes.Fold);
      break;

      case 4:
        validActions.add(GameActionTypes.Call);
        validActions.add(GameActionTypes.Fold);
        validActions.add(GameActionTypes.Raise);
      break;

      case 5:
        validActions.add(GameActionTypes.Call);
        validActions.add(GameActionTypes.Fold);
        validActions.add(GameActionTypes.Raise);
      break;

      case 6:
        validActions.add(GameActionTypes.ShowCards);
        validActions.add(GameActionTypes.Muck);
      break;
    }

    return null;
  }





  /**
   * Check to see if this game can be started right now
   * @return True if game is in a state which can be started, false if not
   */
  public boolean CanGameStart()
  {
    // **** check for game already in progress ****

    if (_currentRound != 0)
    {
      return false;
    }

    // **** check for an invalid number of players ****

    if ((_playerCount < 2) || (_playerCount > 7))
    {
      return false;
    }

    // **** check for invalid low/high stakes ****
    
    if ((_lowStake < 1) ||
        (_lowStake >= _highStake) ||
        (_highStake < 2))
    {
      return false;
    }

    // **** everything passes ****

    return true;
  }


}