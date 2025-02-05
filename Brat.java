import javax.sound.midi.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Main class for the Brat music-making program:
 * - A main window that can:
 *    - Open Drums window (separate).
 *    - Create multiple Instrument windows.
 * - Each InstrumentWindow can Record, Stop, Play on its own channel, and optionally loop.
 * - DrumsWindow plays a drum loop with adjustable tempo.
 */
public class Brat {
    // -----------------------------------------------------
    // GLOBAL SYNTH REFERENCE + GUI COLOR
    // -----------------------------------------------------
    static Synthesizer synth;
    static final Color BG_COLOR = new Color(0x8ACE00);

    // -----------------------------------------------------
    // Keep track of all active instrument windows
    // -----------------------------------------------------
    static List<InstrumentWindow> instrumentWindows = new ArrayList<>();

    public static void main(String[] args) {
        // Initialize MIDI
        try {
            synth = MidiSystem.getSynthesizer();
            synth.open();
        } catch (MidiUnavailableException e) {
            e.printStackTrace();
            return;
        }

        // -------------------------------------------------
        // MAIN WINDOW
        // -------------------------------------------------
        JFrame mainFrame = new JFrame("Brat Main Manager");
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.setSize(900, 600);
        mainFrame.setLayout(new BorderLayout());
        mainFrame.getContentPane().setBackground(BG_COLOR);

        // Optionally load an icon (if brat.png exists alongside your .class/.jar in the workspace you're in) - doesn't work on mac :(
        ImageIcon image = new ImageIcon("brat.png");
        mainFrame.setIconImage(image.getImage());

        // ---------- TOP: Big Title "brat" ----------
        JPanel topPanel = new JPanel(null);
        topPanel.setPreferredSize(new Dimension(600, 100));
        topPanel.setBackground(BG_COLOR);

        JLabel titleLabel = new JLabel("brat", JLabel.CENTER);
        titleLabel.setForeground(Color.BLACK);
        Font baseFont = new Font("Arial Narrow", Font.PLAIN, 60);
        AffineTransform transform = new AffineTransform();
        transform.scale(0.7, 1.0);
        Font stretchedFont = baseFont.deriveFont(transform);
        titleLabel.setFont(stretchedFont);
        titleLabel.setBounds(200, 10, 200, 80);
        topPanel.add(titleLabel);

        mainFrame.add(topPanel, BorderLayout.NORTH);

        // ---------- CENTER: Note or instructions ----------
        JPanel centerPanel = new JPanel();
        centerPanel.setBackground(BG_COLOR);
        centerPanel.add(new JLabel("Create instruments or open drums to start playing!"));
        mainFrame.add(centerPanel, BorderLayout.CENTER);

        // ---------- BOTTOM: Drums + Instruments ----------
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        bottomPanel.setBackground(BG_COLOR);

        // Drums
        JButton openDrumsBtn = new JButton("Open Drums");
        openDrumsBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                new DrumsWindow();
            }
        });
        bottomPanel.add(openDrumsBtn);

        // Instrument selection
        JLabel addInstrLabel = new JLabel("Add Instrument:");
        bottomPanel.add(addInstrLabel);

        String[] instruments = { "Piano", "Guitar", "Synth Lead", "Bass", "Drums" };
        final JComboBox<String> instrumentSelector = new JComboBox<>(instruments);
        bottomPanel.add(instrumentSelector);

        JButton addInstrumentBtn = new JButton("Add Instrument");
        addInstrumentBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String selected = (String) instrumentSelector.getSelectedItem();
                InstrumentWindow w = new InstrumentWindow(selected);
                instrumentWindows.add(w);
            }
        });
        bottomPanel.add(addInstrumentBtn);

        // Play All / Stop All
        JButton playAllBtn = new JButton("Play All Instruments");
        playAllBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                for (InstrumentWindow w : instrumentWindows) {
                    w.playRecording();
                }
            }
        });
        bottomPanel.add(playAllBtn);

        JButton stopAllBtn = new JButton("Stop All Instruments");
        stopAllBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                for (InstrumentWindow w : instrumentWindows) {
                    w.stopPlayback();
                }
            }
        });
        bottomPanel.add(stopAllBtn);

        mainFrame.add(bottomPanel, BorderLayout.SOUTH);
        mainFrame.setVisible(true);
    }
}

/**
 * DrumsWindow:
 * - Plays a drum loop on channel 9
 * - Tempo can be adjusted, automatically restarts loop if playing
 */
class DrumsWindow extends JFrame {
    private static int beatIndex = 0;
    private static ScheduledExecutorService drumScheduler;

    // Basic pattern: Kick(36), HiHat(42), Snare(38), etc.
    private static final int[] drumPattern = { 36, 42, 38, 42, 36, 46, 38, 42 };
    private static final int[] velocities  = { 127, 90, 110, 90, 127, 100, 115, 90 };

    private static volatile boolean isDrumsPlaying = false;
    private static volatile int tempoMs = 150; // Start tempo

    private MidiChannel drumChannel;

    public DrumsWindow() {
        super("Brat Drums");
        setSize(400, 300);
        setResizable(false);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        getContentPane().setBackground(Brat.BG_COLOR);
        setLayout(new FlowLayout(FlowLayout.CENTER, 10, 10));

        // channel 9 => typical General MIDI drum channel
        drumChannel = Brat.synth.getChannels()[9];

        JButton playBtn = new JButton("Play Drums");
        playBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                startDrums();
            }
        });
        add(playBtn);

        JButton stopBtn = new JButton("Stop Drums");
        stopBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                stopDrums();
            }
        });
        add(stopBtn);

        JButton tempoUp = new JButton("Tempo +");
        tempoUp.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                // Increase speed by decreasing ms
                tempoMs = Math.max(20, tempoMs - 10);
                if (isDrumsPlaying) {
                    stopDrums();
                    startDrums();
                }
            }
        });
        add(tempoUp);

        JButton tempoDown = new JButton("Tempo -");
        tempoDown.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                // Decrease speed by increasing ms
                tempoMs = Math.min(1000, tempoMs + 10);
                if (isDrumsPlaying) {
                    stopDrums();
                    startDrums();
                }
            }
        });
        add(tempoDown);

        setVisible(true);
    }

    private void startDrums() {
        if (isDrumsPlaying) return;
        isDrumsPlaying = true;
        beatIndex = 0;

        drumScheduler = Executors.newScheduledThreadPool(1);
        drumScheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                drumChannel.noteOn(drumPattern[beatIndex], velocities[beatIndex]);
                // Disambiguate Swing Timer with fully qualified name
                new javax.swing.Timer(50, new ActionListener() {
                    public void actionPerformed(ActionEvent evt) {
                        drumChannel.noteOff(drumPattern[beatIndex]);
                    }
                }).start();

                beatIndex = (beatIndex + 1) % drumPattern.length;
            }
        }, 0, tempoMs, TimeUnit.MILLISECONDS);
    }

    private void stopDrums() {
        isDrumsPlaying = false;
        if (drumScheduler != null) {
            drumScheduler.shutdownNow();
            drumScheduler = null;
        }
    }
}

/**
 * InstrumentWindow:
 * - Each window records/plays notes on its own channel
 * - Uses a switch statement to avoid map-based lookups
 * - Supports looping if 'Loop' is turned on
 */
class InstrumentWindow extends JFrame {
    private final String instrumentName;
    private final MidiChannel instrumentChannel;
    private final int instrumentProgram;

    // For local recording
    private boolean isRecording = false;
    private long recordingStart;
    private final ArrayList<int[]> recordedNotes = new ArrayList<int[]>();

    // For local playback + looping
    private ScheduledExecutorService playbackScheduler;
    private boolean isLooping = false; // toggled by 'Loop' button

    // For channel selection
    private static int nextChannelIndex = 0;

    public InstrumentWindow(String instrumentName) {
        super("Brat " + instrumentName);
        this.instrumentName = instrumentName;

        setSize(400, 330); // slightly taller to fit 'Loop' button
        setResizable(false);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        getContentPane().setBackground(Brat.BG_COLOR);
        setLayout(new FlowLayout(FlowLayout.CENTER, 10, 10));

        // Assign channel + program
        instrumentChannel = findAvailableChannel();
        instrumentProgram = pickProgram(instrumentName);
        instrumentChannel.programChange(instrumentProgram);

        add(new JLabel("Instrument: " + instrumentName));

        // Record
        JButton recordBtn = new JButton("Record");
        recordBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                startRecording();
                requestFocusInWindow();
            }
        });
        add(recordBtn);

        // Stop
        JButton stopBtn = new JButton("Stop");
        stopBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                stopRecording();
            }
        });
        add(stopBtn);

        // Play
        JButton playBtn = new JButton("Play");
        playBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                playRecording();
            }
        });
        add(playBtn);

        // Loop toggle
        JButton loopBtn = new JButton("Loop: OFF");
        loopBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!isLooping) {
                    isLooping = true;
                    loopBtn.setText("Loop: ON");
                } else {
                    isLooping = false;
                    loopBtn.setText("Loop: OFF");
                }
            }
        });
        add(loopBtn);

        // Key listener to handle note on/off
        setFocusable(true);
        addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                int note = mapKeyToNote(e.getKeyChar());
                if (note >= 0) {
                    instrumentChannel.noteOn(note, 100);
                    if (isRecording) {
                        long delta = System.currentTimeMillis() - recordingStart;
                        recordedNotes.add(new int[]{ note, (int)delta });
                    }
                }
            }
            public void keyReleased(KeyEvent e) {
                int note = mapKeyToNote(e.getKeyChar());
                if (note >= 0) {
                    instrumentChannel.noteOff(note);
                }
            }
        });

        setVisible(true);
        requestFocusInWindow();
    }

    // -------------- KEY -> NOTE (no Maps) --------------
    private int mapKeyToNote(char c) {
        switch (c) {
            case 'a': return 60; // C4
            case 's': return 62; // D4
            case 'd': return 64; // E4
            case 'f': return 65; // F4
            case 'g': return 67; // G4
            case 'h': return 69; // A4
            case 'j': return 71; // B4
            case 'k': return 72; // C5
            default:  return -1;
        }
    }

    // -------------- RECORDING --------------
    private void startRecording() {
        isRecording = true;
        removeAllNotes(); // remove old track manually
        recordingStart = System.currentTimeMillis();
        JOptionPane.showMessageDialog(this, "Recording " + instrumentName + "...");
    }

    private void stopRecording() {
        isRecording = false;
        JOptionPane.showMessageDialog(this, "Stopped recording " + instrumentName + ".");
    }

    // -------------- PLAYBACK (with optional looping) --------------
    public void playRecording() {
        // We do NOT call stopPlayback() here, to let the new playback
        // run concurrently if it was already playing.
        // Instead we schedule fresh notes each time "Play" is pressed.
        if (recordedNotes.size() == 0) {
            JOptionPane.showMessageDialog(this, "No notes recorded for " + instrumentName);
            return;
        }
        schedulePlayback();
    }

    // Schedules the notes once; if isLooping is true, re-schedules after the track ends
    private void schedulePlayback() {
        // First, kill any existing playback, but do NOT set isLooping = false
        stopPlaybackWithoutDisablingLoop();

        playbackScheduler = Executors.newScheduledThreadPool(1);

        int trackLength = 0;
        // Schedule each note
        for (int i = 0; i < recordedNotes.size(); i++) {
            final int[] evt = recordedNotes.get(i);
            final int note = evt[0];
            final int startMs = evt[1];

            if (startMs + 500 > trackLength) {
                trackLength = startMs + 500;
            }
            playbackScheduler.schedule(new Runnable() {
                public void run() {
                    instrumentChannel.noteOn(note, 100);
                }
            }, startMs, TimeUnit.MILLISECONDS);

            playbackScheduler.schedule(new Runnable() {
                public void run() {
                    instrumentChannel.noteOff(note);
                }
            }, startMs + 500, TimeUnit.MILLISECONDS);
        }

        if (isLooping) {
            final int loopDelay = trackLength;
            playbackScheduler.schedule(new Runnable() {
                public void run() {
                    // Only replay if still looping
                    if (isLooping) {
                        schedulePlayback();
                    }
                }
            }, loopDelay, TimeUnit.MILLISECONDS);
        }
    }

    // Called by the user or main window to stop everything
    public void stopPlayback() {
        // Turn off looping
        isLooping = false;
        stopPlaybackWithoutDisablingLoop();
    }

    // Cancels the current playback scheduler but keeps the 'isLooping' state
    private void stopPlaybackWithoutDisablingLoop() {
        if (playbackScheduler != null) {
            playbackScheduler.shutdownNow();
            playbackScheduler = null;
        }
    }

    private void removeAllNotes() {
        while (recordedNotes.size() > 0) {
            recordedNotes.remove(0);
        }
    }

    // -------------- Helpers --------------
    private MidiChannel findAvailableChannel() {
        MidiChannel[] channels = Brat.synth.getChannels();
        // Skip channel 9 (drums) and wrap around if needed
        while (nextChannelIndex == 9 || nextChannelIndex >= channels.length) {
            nextChannelIndex++;
            if (nextChannelIndex >= channels.length) {
                nextChannelIndex = 0;
            }
        }
        MidiChannel ch = channels[nextChannelIndex];
        nextChannelIndex++;
        return ch;
    }

    private int pickProgram(String instr) {
        if (instr.equals("Guitar")) {
            return 25;
        } else if (instr.equals("Synth Lead")) {
            return 80;
        } else if (instr.equals("Bass")) {
            return 34;
        } else if (instr.equals("Drums")) {
            return 118;
        } else {
            // default to Piano
            return 0;
        }
    }
}
