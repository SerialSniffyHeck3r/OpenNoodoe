import io.opennoodoe.app.protocol.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Cross-check public Python-generated fixtures against the production Java codec. */
public final class ProtocolCheck {
    static byte[] hex(String s) {
        byte[] out = new byte[s.length()/2];
        for (int i=0;i<out.length;i++) out[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16);
        return out;
    }
    static void equal(byte[] got,String expected,String label) {
        if (!Arrays.equals(got,hex(expected))) throw new AssertionError(label);
    }
    public static void main(String[] args) throws Exception {
        int count=0;
        for(String line:Files.readAllLines(Paths.get(args[0]),StandardCharsets.UTF_8)) {
            if(line.startsWith("#")||line.isEmpty())continue;
            String[] f=line.split("\t",-1);
            byte[] payload=hex(f[7]);
            CommandFrame cmd=new CommandFrame(Integer.parseInt(f[1]),Integer.parseInt(f[2]),payload);
            equal(cmd.encode(),f[8],f[0]+" inner");
            SequenceFrame seq=new SequenceFrame(Integer.parseInt(f[3]),Integer.parseInt(f[4]),
                    Integer.parseInt(f[5]),Integer.parseInt(f[6]),cmd.encode());
            equal(seq.encode(),f[9],f[0]+" outer");
            equal(SequenceFrame.decode(hex(f[9])).getPayload(),f[8],f[0]+" decode");
            if(FileTransferPayloads.paddedCrc32(payload)!=Long.parseUnsignedLong(f[10],16))
                throw new AssertionError(f[0]+" CRC");
            count++;
        }
        equal(FileTransferPayloads.negotiate(1,2,0x600,3,1,new byte[16]),
                "010002000006030000000100000000000000000000000000000000","negotiate");
        equal(new SequenceFrame(0x40,1,128,0,new byte[0]).encode(),"5aff09004001800000","ACK");
        byte[] nav=CandidatePayloads.navigation(123,2,3,1,2,3,4,5,6,7,8,9,10,11,12,false,true,60,0,false,0,90,4);
        if(nav.length!=42)throw new AssertionError("navigation length");
        System.out.println("Production Java cross-check passed: "+count+" vectors, negotiation, ACK, navigation length.");
    }
}
