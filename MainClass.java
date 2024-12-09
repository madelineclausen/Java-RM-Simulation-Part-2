import java.io.*;
import java.io.InputStreamReader;
import java.util.Hashtable;
class Disk
{
    static final int NUM_SECTORS = 2048;
    static final int DISK_DELAY = 80;
    public int sector_counter = 0;
    public StringBuffer sectors[] = new StringBuffer[NUM_SECTORS];
    Disk(){ }
    void write(int sector, StringBuffer data){
        try {
            Thread.sleep(DISK_DELAY);
            sector_counter += 1;
        }
        catch(Exception e) {
            System.out.println("Inside Disk");
        }
        sectors[sector] = new StringBuffer(data);
    }
    StringBuffer read(int sector, StringBuffer data){
        try {
            Thread.sleep(DISK_DELAY);
        }
        catch(Exception e) {}
        return sectors[sector];
    }
}

class Printer
{
    static final int PRINT_DELAY = 275;
    int name;
    String printer = "PRINTER";
    Printer(int name){ this.name = name; }
    void print(StringBuffer data) {
        try {
            Thread.sleep(PRINT_DELAY);
            FileWriter printed_file = new FileWriter((printer + Integer.toString(this.name)), true);
            printed_file.write(data.toString() + "\n");
            printed_file.flush();
            printed_file.close();
        }
        catch(Exception e) {}
    }
}

class PrintJobThread extends Thread {
    StringBuffer bufferFileName;
    int printerId;
    String file_name;
    DiskManager dm;
    PrinterManager pm;

    PrintJobThread(String file, int printerId, PrinterManager pm, DiskManager dm){
        this.dm = dm;
        this.pm = pm;
        this.printerId = printerId;
        this.file_name = file;
        this.bufferFileName = new StringBuffer(file);
        //this.string_buffer;
    }

    public void run() {
        try {
            FileInfo findFile = dm.dir.lookup(this.bufferFileName);
            int length = findFile.fileLength;
            int diskNum = findFile.diskNumber;
            int startingSector = findFile.startingSector;

            for (int i = 0; i < length; i++) {
                Disk disk = dm.disks[diskNum];
                //StringBuffer test = new StringBuffer();
                StringBuffer lines = disk.read(startingSector + i, new StringBuffer());
                pm.printers[printerId].print(lines);
            }
        } catch (Exception e) {
            System.out.println("PrintJobThreadRun");
        }
    }
}

class FileInfo
{
    int diskNumber;
    int startingSector;
    int fileLength;
}

class DirectoryManager
{
    private Hashtable<String, FileInfo> T = new Hashtable<String, FileInfo>();
    DirectoryManager(){}

    void enter(StringBuffer string_buffer, FileInfo f){
        String converted_string = string_buffer.toString();
        T.put(converted_string, f);
    }

    FileInfo lookup(StringBuffer string_buffer){
        String converted_string = string_buffer.toString();
        return T.get(converted_string);
    }
}

class ResourceManager {
    boolean isFree[];
    ResourceManager(int numberOfItems) {
        isFree = new boolean[numberOfItems];
        for (int i=0; i<isFree.length; ++i)
        {
            isFree[i] = true;
        }
    }
    synchronized int request()
    {
        while (true) {
            for (int i = 0; i < isFree.length; ++i){
                if (isFree[i]) {
                    isFree[i] = false;
                    return i;
                }
            }
            try {
                this.wait();
            }
            catch (InterruptedException e) {
                System.out.println("inside resouce manager request");
            }
        }
    }
    synchronized void release(int index) {
        isFree[index] = true;
        this.notify();
    }
}

class DiskManager extends ResourceManager
{
    Disk disks[];
    DirectoryManager dir = new DirectoryManager();
    DiskManager(Disk disks[], int num_disks) {
        super(num_disks);
        this.disks = disks;
    }
}

class PrinterManager extends ResourceManager
{
    Printer printers[];
    PrinterManager(Printer total_printers[], int p_count)
    {
        super(p_count);
        this.printers = total_printers;
    }
}

class UserThread extends Thread
{
    String file_name;
    FileInputStream input;
    String userNum;
    DiskManager dm;
    PrinterManager pm;
    int curr;
    String currentLine;
    BufferedReader b_read;
    UserThread(int id, DiskManager dm, PrinterManager pm)
    {
        this.userNum = Integer.toString(id);
        this.dm = dm;
        this.pm = pm;
        try {
            this.input = new FileInputStream("USER" + this.userNum);
        } catch (FileNotFoundException e) {
            System.out.println("In UserThread Constructor");
        }
        this.b_read = new BufferedReader(new InputStreamReader(this.input));

    }
    public void run()
    {
        try{
            processCommandsIn(b_read);
            input.close();
            b_read.close();
        }
        catch (Exception e){ System.out.println("Inside UserThread run()");}
    }
    void processCommandsIn(BufferedReader in){
        try{
            String newL = in.readLine();
            while (newL != null) {
                if (newL.startsWith(".save")){
                    this.curr = dm.request();
                    FileInfo findFile = new FileInfo();
                    findFile.diskNumber = curr;
                    findFile.startingSector = dm.disks[curr].sector_counter;
                    findFile.fileLength = 0;
                    
                    this.currentLine = newL.substring(6);
                    newL = in.readLine();
                    
                    while (!(newL.startsWith(".end"))) {
                        int curr_sector = dm.disks[curr].sector_counter; //int curr_sector = findFile.startingSector + findFile.fileLength;
                        dm.disks[curr].write(curr_sector, new StringBuffer(newL)); //
                        findFile.fileLength += 1;
                        newL = in.readLine();
                    }
                    
                    dm.dir.enter(new StringBuffer(this.currentLine), findFile);
                    dm.release(curr);
    
                }
    
                if (newL.startsWith(".print")){
                    print(newL);
                }
                newL = in.readLine();
            }
        }catch (Exception E){ System.out.println("Inside processCommandsIn"); }
    }

    void print(String test){
        String line = test.substring(7);
        int printId = pm.request();
        PrintJobThread printJob = new PrintJobThread(line, printId, pm, dm);
        try { //maybe an issue with the try + catch statement
            printJob.start();
            printJob.join();
            pm.release(printId);
        } catch (Exception e) { System.out.println("Inside UserThread print");}
        //return printJob;
    }

    void endFile(FileInfo fI){
        StringBuffer newRead = new StringBuffer(this.curr); //or newL
        dm.dir.enter(newRead, fI);
        dm.release(this.curr);
    }

    FileInfo save() {
            this.curr = dm.request();
            FileInfo findFile = new FileInfo();

            findFile.diskNumber = curr;
            findFile.startingSector = dm.disks[curr].sector_counter;
            findFile.fileLength = 0;

            StringBuffer curr_line = new StringBuffer(this.currentLine);
            int curr_sector = dm.disks[curr].sector_counter; //int curr_sector = findFile.startingSector + findFile.fileLength;
            dm.disks[curr].write(curr_sector, curr_line); //
            findFile.fileLength += 1;

            /*while (!(newLine.startsWith(".end"))) {
                StringBuffer curr_line = new StringBuffer(newLine);
                int curr_sector = dm.disks[curr].sector_counter; //int curr_sector = findFile.startingSector + findFile.fileLength;
                dm.disks[curr].write(curr_sector, curr_line); //
                findFile.fileLength += 1;
                newLine = in.readLine();
            }*/

            /*
            For the end
            StringBuffer newRead = new StringBuffer(read);
            dm.dir.enter(newRead, findFile);
            dm.release(curr);
             */
            return findFile;
    }
}


class OS141
{
    int NUM_USERS = 4, NUM_DISKS = 2, NUM_PRINTERS = 3;
    String userFileNames[];
    UserThread users[];
    Disk disks[];
    Printer printers[];
    DiskManager diskManager;
    PrinterManager printerManager;
    static OS141 ins = null;
    String argv[];

    OS141(String argv[]) {
        this.argv = argv;
    }
    static OS141 instance(String argv[]) {
        if (ins == null) { ins = new OS141(argv); }
        return ins;
    }
    void configure(String argv[]) {
        NUM_DISKS = Math.abs(Integer.parseInt(argv[1]));
        disks = new Disk[NUM_DISKS];
        for(int i = 0; i < NUM_DISKS; i++) {
            disks[i] = new Disk();
        }

        diskManager = new DiskManager(disks, NUM_DISKS);
        NUM_PRINTERS = Math.abs(Integer.parseInt(argv[2]));
        printers = new Printer[NUM_PRINTERS];
        for(int i = 0; i < NUM_PRINTERS; i++) {
            printers[i] = new Printer(i);
        }

        printerManager = new PrinterManager(printers, NUM_PRINTERS);
        NUM_USERS = Math.abs(Integer.parseInt(argv[0]));
        users = new UserThread[NUM_USERS];
        for(int i = 0; i < NUM_USERS; i++) {
            users[i] = new UserThread(i, diskManager, printerManager);
        }

        for(int i = 0; i < NUM_USERS; i++) {
            users[i].start();
        }

        for(int i = 0; i < NUM_USERS; i++) {
            try { users[i].join();}
            catch(Exception e) {
                System.out.println("joinUserThreads");
            }
        }
    }

}

public class MainClass
{
    public static void main(String args[])
    {
        OS141 start = OS141.instance(args);
        start.configure(args);
    }
}