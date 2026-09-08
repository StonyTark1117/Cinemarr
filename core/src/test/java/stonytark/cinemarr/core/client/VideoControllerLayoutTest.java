package stonytark.cinemarr.core.client;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import stonytark.cinemarr.core.client.VideoControllerLayout.Box;
import stonytark.cinemarr.core.client.VideoControllerLayout.Slot;

class VideoControllerLayoutTest {
    @Test void everyControlAndTextRowFitsWithoutOverlapAtAllNormalScales(){
        for(int width:new int[]{320,321,360,480,576,591,592,593,640,760,776,1280,1920})
            for(int height:new int[]{240,270,360,480,720,1080})
                for(boolean queue:new boolean[]{false,true}){
                    VideoControllerLayout layout=new VideoControllerLayout(width,height);
                    List<Box> boxes=new ArrayList<Box>();
                    for(Slot slot:Slot.values())boxes.add(layout.slot(slot));
                    for(int i=0;i<layout.libraryCapacity();i++)boxes.add(layout.library(i));
                    boxes.add(new Box(layout.left(),6,layout.panel(),9));
                    boxes.add(new Box(layout.left(),18,layout.panel(),9));
                    boxes.add(new Box(layout.left(),layout.noticeY(),layout.panel(),9));
                    for(int row=0;row<layout.rows(queue);row++)
                        boxes.add(new Box(layout.left(),layout.contentTop()+row*22,layout.panel(),20));
                    if(!queue)boxes.add(new Box(layout.left(),layout.pagerY(),200,20));
                    for(int i=0;i<boxes.size();i++){
                        assertTrue(boxes.get(i).fits(width,height),width+"x"+height+" box "+i);
                        for(int j=0;j<i;j++)assertFalse(boxes.get(i).overlaps(boxes.get(j)),
                                width+"x"+height+" boxes "+i+","+j);
                    }
                }
    }
    @Test void allSixtyFourLibrariesHaveExactlyOneReachablePage(){
        for(int width:new int[]{320,480,640,1920})for(int count=0;count<=64;count++){
            VideoControllerLayout layout=new VideoControllerLayout(width,240);
            int seen=0;
            for(int page=0;page<layout.libraryPages(count);page++)
                for(int slot=0;slot<layout.libraryCapacity();slot++)
                    if(page*layout.libraryCapacity()+slot<count)seen++;
            assertEquals(count,seen);
        }
    }
}
