package stonytark.cinemarr.core.client;

import org.junit.jupiter.api.Test;
import stonytark.cinemarr.core.network.Hashing;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VideoSegmentAssemblerTest {
    @Test void reassemblesOutOfOrderChunksAndVerifiesTheWholeSegment() {
        UUID session=UUID.randomUUID(); byte[] complete=new byte[20_000]; for(int i=0;i<complete.length;i++)complete[i]=(byte)i;
        byte[] first=Arrays.copyOfRange(complete,0,16_384),second=Arrays.copyOfRange(complete,16_384,complete.length); String hash=Hashing.sha256(complete);
        VideoSegmentAssembler assembler=new VideoSegmentAssembler(); assembler.begin(session,3,7,2,2,hash,4_000,true);
        assertFalse(assembler.accept(session,3,7,2,1,2,hash,4_000,true,second).isPresent());
        Optional<VideoSegmentAssembler.CompletedSegment> result=assembler.accept(session,3,7,2,0,2,hash,4_000,true,first);
        assertTrue(result.isPresent()); assertArrayEquals(complete,result.get().data());
    }

    @Test void rejectsStaleGenerationTamperingAndConflictingDuplicateChunks() {
        UUID session=UUID.randomUUID(); byte[] complete={1,2,3}; String hash=Hashing.sha256(complete);
        VideoSegmentAssembler assembler=new VideoSegmentAssembler(); assembler.begin(session,3,7,2,1,hash,4_000,true);
        assertFalse(assembler.accept(session,2,7,2,0,1,hash,4_000,true,complete).isPresent());
        assertFalse(assembler.accept(session,3,8,2,0,1,hash,4_000,true,complete).isPresent());
        assertFalse(assembler.accept(session,3,7,2,0,1,hash,4_000,true,new byte[]{9}).isPresent());
        assembler.begin(session,3,8,2,2,hash,4_000,true);
        assertFalse(assembler.accept(session,3,8,2,0,2,hash,4_000,true,new byte[]{1}).isPresent());
        assertFalse(assembler.accept(session,3,8,2,0,2,hash,4_000,true,new byte[]{2}).isPresent());
    }
}
