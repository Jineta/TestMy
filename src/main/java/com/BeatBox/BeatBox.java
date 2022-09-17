package com.BeatBox;

import javax.sound.midi.*;
import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;

public class BeatBox {
    JFrame theFrame;
    JPanel mainPanel;
    JList incomingList;
    JTextField userMessage;
    ArrayList<JCheckBox> checkboxList;
    int nextNum;
    Vector<String> listVector = new Vector<String>();
    String userName;
    ObjectOutputStream out;
    ObjectInputStream in;
    HashMap<String, boolean[]> otherSeqsMap = new HashMap<String, boolean[]>();

    Sequencer sequencer;
    Sequence sequence;
    Sequence mySequence = null;
    Track track;

    String[] instrumentNames = {"Bass Drum", "Closed Hi-Hat", "Open Hi-Hat", "Acoustic Snare", "Crash Cymbal", "Hand Clap", "High Tom",
            "Hi Bongo", "Maracas", "Wristle", "Low Conga", "Cowbell", "Vibraslap", "Low-mid Tom", "High Agogo", "Open Hi Conga"};
    int[] instruments = {35, 42, 46, 38, 49, 39, 50, 60, 70, 72, 64, 56, 58, 47, 67, 63};

    public static void main(String[] args) {
        new BeatBox().startUP(args[0]); //используем аргумент командной строки в качестве имени, которое будет выводиться на экране. Например %Java BeatBox the Flash
        //это наш пользовательский идентификатор(отображаемое имя)
    }

    public void startUP(String userName) {
        //Настройка сети и ввода/вывода, создани и запуск потока для чтения сообщений.
        this.userName = userName;
        // Открываем соединение с сервером
        try {
            Socket sock = new Socket("127.0.0.1", 4242); // этой строкой мы запрашиваем у сервера доступ на соединение
            //писать на сервер
            out = new ObjectOutputStream(sock.getOutputStream());
            //читать с сервера
            in = new ObjectInputStream(sock.getInputStream());
            //передаем экземпляр класса, реализующего Runnable в конструктор Thread. Благодаря этому поток знает, акой метод нужно поместить на дно нового стека
            //RemoteReader() содержит в себе метод run(), в котором выполняется десериализация
            // Теперь есть эземплр объекта Тред, но исполняемого потока все еще нет
            Thread remote = new Thread(new RemoteReader());
            //запуск потока
            //теперь пооток становиться работоспособным и просто ждет своей очереди. У потока уже есть вой стек вызовов
            remote.start();
        } catch (Exception ex) {
            System.out.println("Couldn't connect - you'll have to play alone.");
        }
        setUpMidi();
        buildGUI();
    }

    public void buildGUI() {
        theFrame = new JFrame("Cyber BeatBox");
        BorderLayout layout = new BorderLayout();
        JPanel background = new JPanel(layout);
        background.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        checkboxList = new ArrayList<JCheckBox>();
        Box buttonBox = new Box(BoxLayout.Y_AXIS);
        JButton start = new JButton("Start");
        start.addActionListener(new MyStartListener());
        buttonBox.add(start);

        JButton stop = new JButton("Stop");
        stop.addActionListener(new MyStopListener());
        buttonBox.add(stop);

        JButton upTempo = new JButton("Tempo Up");
        upTempo.addActionListener(new MyUpTempoListener());
        buttonBox.add(upTempo);

        JButton downTempo = new JButton("Tempo down");
        upTempo.addActionListener(new MyDownTempoListener());
        buttonBox.add(downTempo);

        JButton sendIt = new JButton("sendIt");
        upTempo.addActionListener(new MySendListener());
        buttonBox.add(sendIt);

        userMessage = new JTextField();
        buttonBox.add(userMessage);
        //JList  это компонент , в кот. отображаются входящие сообщения, которые можно выбирать из списка, а не только просмотривать.
        // Благодаря ему мы всегда можем загружать и воспроизводить прикрепленые к ним музыкальные шаблоны
        incomingList = new JList();
        incomingList.addListSelectionListener(new MyListSelectionListener());
        incomingList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane theList = new JScrollPane(incomingList);
        buttonBox.add(theList);
        incomingList.setListData(listVector); // no initial data

        Box nameBox = new Box(BoxLayout.Y_AXIS);
        for (int i = 0; i < 16; i++) {
            nameBox.add(new Label(instrumentNames[i]));
        }

        background.add(BorderLayout.EAST, buttonBox);
        background.add(BorderLayout.WEST, nameBox);

        theFrame.getContentPane().add(background);
        GridLayout grid = new GridLayout(16, 16);
        grid.setVgap(1);
        grid.setHgap(2);
        mainPanel = new JPanel(grid);
        background.add(BorderLayout.CENTER, mainPanel);

        for (int i = 0; i < 256; i++) {
            JCheckBox c = new JCheckBox();
            c.setSelected(false);
            checkboxList.add(c);
            mainPanel.add(c);
        }

        theFrame.setBounds(50, 50, 300, 300);
        theFrame.pack();
        theFrame.setVisible(true);
    }

    public void setUpMidi() {
        try {
            sequencer = MidiSystem.getSequencer();
            sequencer.open();
            sequence = new Sequence(Sequence.PPQ, 4);
            track = sequence.createTrack();
            sequencer.setTempoInBPM(120);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void buildTrackAndStart() {
        ArrayList<Integer> trackList = null;//тут будут храниться инструменты для каждого трека
        sequence.deleteTrack(track);
        track = sequence.createTrack();
        // создаем трек, проверяя состояние всех флажков и связывая их с инструментом (для каждого создается MidiEvent).
        for (int i = 0; i < 16; i++) {
            trackList = new ArrayList<Integer>();
            for (int j = 0; j < 16; j++) {
                JCheckBox jc = (JCheckBox) checkboxList.get(j + (16 * i));
                if (jc.isSelected()) {
                    int key = instruments[i];
                    trackList.add(new Integer(key));
                } else {
                    trackList.add(null); //этот слот в трекедолжен быть пустым
                }
            }
            makeTracks(trackList);
        }
        track.add(makeEvent(192, 9, 1, 0, 15)); //в результате мы всегда имеем 16 тактов
        try {
            sequencer.setSequence(sequence);
            sequencer.setLoopCount(sequencer.LOOP_CONTINUOUSLY);
            sequencer.start();
            sequencer.setTempoInBPM(120);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //слушатели пользовательского интерфейса
    public class MyStartListener implements ActionListener {
        public void actionPerformed(ActionEvent a) {
            buildTrackAndStart();
        }
    }

    public class MyStopListener implements ActionListener {
        public void actionPerformed(ActionEvent a) {
            sequencer.stop();
        }
    }

    public class MyUpTempoListener implements ActionListener {
        public void actionPerformed(ActionEvent a) {
            float tempoFactor = sequencer.getTempoFactor();
            sequencer.setTempoFactor((float) (tempoFactor * 1.03));
        }
    }

    public class MyDownTempoListener implements ActionListener {
        public void actionPerformed(ActionEvent a) {
            float tempoFactor = sequencer.getTempoFactor();
            sequencer.setTempoFactor((float) (tempoFactor * 0.97));
        }
    }

    public class MySendListener implements ActionListener {
        public void actionPerformed(ActionEvent a) {
            //создаем массим в котором будут хранитьсятолько состояния флажков
            boolean[] checkboxState = new boolean[256];
            for (int i = 0; i < 256; i++) {
                JCheckBox check = (JCheckBox) checkboxList.get(i);
                //вместо отправки строкового сообщения мы сериализовываем два объекта (строковое сообщение и музыкальный шаблон) и записываем их в исходящий поток сокета (на сервер)

                if (check.isSelected()) {
                    checkboxState[i] = true;
                }
            }
            String messageToSend = null;
            try {
                out.writeObject(userName + nextNum++ + ": " + userMessage.getText());
                out.writeObject(checkboxState);
            } catch (Exception ex) {
                System.out.println("Sorry dude. Couldn't send it to the server.");
            }
            userMessage.setText("");
        }
    } // close class MySendListener

    public class MyListSelectionListener implements ListSelectionListener {
        public void valueChanged(ListSelectionEvent le) {
            if (!le.getValueIsAdjusting()) {
                String selected = (String) incomingList.getSelectedValue();
                if (selected != null) {
                    //переходим к отображению и изменяем последовательность
                    boolean[] selectedState = (boolean[]) otherSeqsMap.get(selected);
                    changeSequence(selectedState);
                    sequencer.stop();
                    buildTrackAndStart();
                    //ListSelectionListened срабатывает, когда пользователь выбирает сообщения из списка. При этом мы сразу загружаем соответствующий музыкальный шаблон (хранящийся в переменной otherSeqsMap типа HashMap) и указываем проигрывать его.
                    //Мы добавили несколько условий if  из-за особенностей, связанных с получением событий ListSelectionEvent

                }
            }
        }
    }// close class ListSelectionListener

    public class RemoteReader implements Runnable {
        boolean[] checkboxState = null;
        String nameToShow = null;
        Object obj = null;

        public void run() {
            try {
                //когда приходит сообщение мы считываем(десериализовываем
                while ((obj = in.readObject()) != null) {
                    System.out.println("got an object from server");
                    System.out.println(obj.getClass());
                    String nameToShow = (String) obj;
                    checkboxState = (boolean[]) in.readObject();
                    otherSeqsMap.put(nameToShow, checkboxState);
                    listVector.add(nameToShow);
                    incomingList.setListData(listVector);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    } // close class RemoteReader

    public class MyPlayMineListener implements ActionListener {
        public void actionPerformed(ActionEvent a) {
            if (mySequence != null) {
                sequence = mySequence; //восстановление до оригинальной последовательности
            } // этот метод вызывается, когда пользователь выбирает пункт  из списка.
            // Мы немедленно устанавливаем выбранный шаблон
        }
    }

    public void changeSequence(boolean[] checkboxState) {
        for (int i = 0; i < 256; i++) {
            JCheckBox check = (JCheckBox) checkboxList.get(i);
            if (checkboxState[i]) {
                check.setSelected(true);
            } else {
                check.setSelected(false);
            }
        }
    }

    public void makeTracks(ArrayList list) {
        Iterator it = list.iterator();
        for (int i = 0; i < 16; i++) {
            Integer num = (Integer) it.next();
            if (num != null) {
                int numKey = num.intValue();
                track.add(makeEvent(144, 9, numKey, 100, i));
                track.add(makeEvent(128, 9, numKey, 100, i + 1));
            }
        }
    }

    public MidiEvent makeEvent(int comd, int chan, int one, int two, int tick) {
        MidiEvent event = null;
        try {
            ShortMessage a = new ShortMessage();
            a.setMessage(comd, chan, one, two);
            event = new MidiEvent(a, tick);
        } catch (Exception e) {
        }
        return event;
    }
}

