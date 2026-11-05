package survival.balance.tests;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;

/** Test-observer output only: a reader lock must never escape into native gameplay. */
final class RetryingJsonlWriter {
    private final Path path;
    private final ArrayDeque<byte[]> pending=new ArrayDeque<>();
    private long committed=-1,failures;
    private boolean delayed;

    RetryingJsonlWriter(Path path){this.path=path;}
    void append(String row){pending.addLast((row+"\n").getBytes(StandardCharsets.UTF_8));flush();}
    int pendingRows(){return pending.size();}
    long failures(){return failures;}

    void flush(){
        if(pending.isEmpty())return;
        try{
            Files.createDirectories(path.getParent());
            // Reopen at the last complete row, never blindly append after a partial write.
            try(FileChannel channel=FileChannel.open(path,StandardOpenOption.CREATE,StandardOpenOption.WRITE)){
                if(committed<0)committed=channel.size();
                channel.position(committed);
                int rows=0,bytes=0;
                while(!pending.isEmpty()&&rows<256&&bytes<1_048_576){
                    byte[] row=pending.peekFirst();ByteBuffer buffer=ByteBuffer.wrap(row);
                    while(buffer.hasRemaining())if(channel.write(buffer)==0)throw new IOException("No progress writing observer row");
                    committed+=row.length;pending.removeFirst();rows++;bytes+=row.length;
                }
            }
            if(delayed&&pending.isEmpty()){
                System.err.println("BBFIGHT_LOG_RECOVERED file="+path+" failures="+failures);
                delayed=false;
            }
        }catch(IOException error){
            failures++;
            if(!delayed)System.err.println("BBFIGHT_LOG_DELAYED file="+path+" pendingRows="+pending.size()+" error="+error);
            delayed=true;
        }
    }

    /** Preserve exact pending bytes and their replay offset if shutdown occurs while locked. */
    void preservePending(){
        flush();if(pending.isEmpty())return;
        Path recovery=path.resolveSibling(path.getFileName()+".pending-recovery");
        try{
            Files.createDirectories(recovery.getParent());
            try(var out=Files.newOutputStream(recovery,StandardOpenOption.CREATE_NEW)){
                out.write(("{\"committedByteOffset\":"+committed+",\"pendingRows\":"+pending.size()+"}\n").getBytes(StandardCharsets.UTF_8));
                for(byte[] row:pending)out.write(row);
            }
            System.err.println("BBFIGHT_LOG_PENDING_PRESERVED file="+recovery+"; encounter publication remains incomplete");
        }catch(IOException error){
            System.err.println("BBFIGHT_LOG_PENDING_NOT_PERSISTED file="+path+" rows="+pending.size()+" error="+error);
        }
    }
}
