package io.neocdtv.player.core.omxplayer;

import io.neocdtv.player.core.ModelUtil;
import io.neocdtv.player.core.PlayerEventsHandler;
import io.neocdtv.player.core.PlayerState;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * OmxPlayer.
 *
 * @author xix
 * @since 03.01.18
 */
public class OmxPlayer {

  private final static Logger LOGGER = Logger.getLogger(OmxPlayer.class.getName());
  private Process process = null;
  private OmxPlayerOutputStreamConsumer stdOutConsumer;
  private OmxPlayerErrorStreamConsumer errOutConsumer;
  private PrintStream stdOut;
  private PlayerState playerState;
  private int volume = -3000;
  private final PlayerEventsHandler playerEventsHandler;
  private static final String OPTION_ADJUST_FRAME_RATE = "-r";
  private static final String OPTION_BLACK_BACKGROUND = "-b";
  private static final String OPTION_PRINT_STATS = "-s";
  private static final String OPTION_PRINT_INFORMATION = "-I";
  private static final String OPTION_START_POSITION = "-l";
  private static final String OPTION_INITIAL_VOLUME= "--vol";
  private static final String COMMAND_PAUSE = "p";
  private static final String COMMAND_QUIT = "q";
  private static final String COMMAND_INCREASE_VOLUME = "+";
  private static final String COMMAND_DECREASE_VOLUME = "-";
  private static final int MAX_VOLUME_IN_MILLIBELS = 600;
  private static final int MIN_VOLUME_IN_MILLIBELS = -6000;
  private final static List<String> CMD = Arrays.asList(
      "omxplayer",
      OPTION_PRINT_INFORMATION,
      OPTION_ADJUST_FRAME_RATE,
      OPTION_BLACK_BACKGROUND,
      OPTION_PRINT_STATS,
      OPTION_START_POSITION);

  public OmxPlayer(final PlayerEventsHandler playerEventsHandler) {
    Runtime.getRuntime().addShutdownHook(cleanupThread);
    playerState = new PlayerState();
    this.playerEventsHandler = playerEventsHandler;
  }

  public void play(final String mediaPath) {
    LOGGER.log(Level.INFO, mediaPath);
    play(mediaPath, 0);
  }

  public void play(final String mediaPath, final long startPosition) {
    stop();
    LOGGER.log(Level.INFO, mediaPath + ", startPosition: " + startPosition);
    playerState = new PlayerState();
    playerState.setCurrentUri(mediaPath);
    playerState.setVolume(volume);
    ArrayList<String> cmdCopy = new ArrayList<>(CMD);
    cmdCopy.add(ModelUtil.toTimeString(startPosition));
    cmdCopy.add(OPTION_INITIAL_VOLUME);
    cmdCopy.add(String.valueOf(volume));
    cmdCopy.add(mediaPath);

    printCommand(cmdCopy);
    ProcessBuilder processBuilder = new ProcessBuilder(cmdCopy);
    try {
      process = processBuilder.start();
      InputStream stdIn = process.getInputStream();
      InputStream errIn = process.getErrorStream();
      stdOut = new PrintStream(process.getOutputStream());

      stdOutConsumer = new OmxPlayerOutputStreamConsumer(stdIn, playerEventsHandler);
      Thread one = new Thread(stdOutConsumer);
      one.start();

      errOutConsumer = new OmxPlayerErrorStreamConsumer(errIn);
      Thread two = new Thread(errOutConsumer);
      two.start();

    } catch (IOException ioException) {
      LOGGER.log(Level.SEVERE, ioException.getMessage(), ioException);
    }
  }

  public void stop() {
    if (isProcessAvailable()) {
      stdOutConsumer.deactivate();
      errOutConsumer.deactivate();
      execute(COMMAND_QUIT);
      try {
        process.waitFor();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

  public void pause() {
    execute(COMMAND_PAUSE);
  }

  public void skip(long seconds) {
    play(playerState.getCurrentUri(), seconds);
  }

  public long getPosition() {
    return playerState.getPosition();
  }

  public long getDuration() {
    return playerState.getDuration();
  }

  public int getVolumeInMillibels() {
    return playerState.getVolumeInMillidels();
  }

  public float getPositionPercentage() {
    throw new RuntimeException("NOT IMPLEMENTED");
  }

  public void increaseVolume() {
    if(volume < MAX_VOLUME_IN_MILLIBELS) {
      execute(COMMAND_INCREASE_VOLUME);
      volume += 300;
      playerState.setVolume(volume);
    }
  }

  public void decreaseVolume() {
    if(volume > MIN_VOLUME_IN_MILLIBELS) {
      execute(COMMAND_DECREASE_VOLUME);
      volume -= 300;
      playerState.setVolume(volume);
    }
  }

  private void execute(final String command) {
    LOGGER.log(Level.INFO, "execute: " + command);
    stdOut.print(command);
    stdOut.flush();
  }

  private Thread cleanupThread = new Thread(this::cleanup);

  private void cleanup() {
    LOGGER.log(Level.INFO, "clean up");
    if (isProcessAvailable()) {
      process.destroy();
    }
  }

  public PlayerState getPlayerState() {
    return playerState;
  }

  private boolean isProcessAvailable() {
    return process != null;
  }

  private void printCommand(final ArrayList<String> cmdCopy) {
    final StringBuffer stringBuffer = new StringBuffer();
    cmdCopy.stream().forEach(o -> stringBuffer.append(o).append(" "));
    LOGGER.info("Executing command: " + stringBuffer.toString());
  }
}
