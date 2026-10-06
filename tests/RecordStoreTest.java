import com.qingsongji.diary.RecordStore;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
public class RecordStoreTest {
  static void check(boolean ok){if(!ok)throw new AssertionError();}
  public static void main(String[] args)throws Exception{
    Path root=Files.createTempDirectory("diary-test");RecordStore store=new RecordStore(root.toFile());
    check(store.read()==null);store.write("first");check(store.read().equals("first"));store.write("second");check(store.read().equals("second"));
    Files.delete(root.resolve("records-a.json"));Files.createDirectory(root.resolve("records-a.json"));
    try{store.write("bad");throw new AssertionError();}catch(java.io.IOException expected){}check(store.read().equals("second"));
    Files.delete(root.resolve("records-a.json"));Files.write(root.resolve("active"),"corrupt".getBytes(StandardCharsets.UTF_8));
    try{store.read();throw new AssertionError();}catch(java.io.IOException expected){}store.write("reset");check(store.read().equals("reset"));
    check(Files.exists(root.resolve("records-b.json")));
    System.out.println("Native journal read/write, failed-write protection and reset checks passed.");
  }
}
