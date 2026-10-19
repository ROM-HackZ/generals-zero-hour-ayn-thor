package com.generalsx.zerohour;
// GeneralsX @feature Codex 14/10/2026 Verify spatial focus and production/command separation.
public final class ThorDeckLayoutTest {
    static void check(boolean ok,String reason) { if(!ok) throw new AssertionError(reason); }
    public static void main(String[] args) {
        float[][] commands=new float[12][];
        for(int i=0;i<12;++i) {
            commands[i]=ThorDeckLayout.command(i);
            check(commands[i][0]>=24 && commands[i][2]<=1216 && commands[i][3]<844,"Commands stay clear of details and safe edges");
            for(int j=0;j<i;++j) check(commands[i][0]>=commands[j][2] || commands[j][0]>=commands[i][2] || commands[i][1]>=commands[j][3] || commands[j][1]>=commands[i][3],"Command targets cannot overlap");
        }
        check(ThorDeckLayout.neighbor(commands,0,1,0)==1,"Right follows visible columns");
        check(ThorDeckLayout.neighbor(commands,0,0,1)==6,"Down follows visible rows");
        check(ThorDeckLayout.neighbor(commands,6,0,-1)==0,"Up returns to same column");
        check(ThorDeckLayout.neighbor(commands,5,1,0)==5,"Right edge permits paging rather than jumping rows");
        // GeneralsX @test Codex 18/10/2026 Text menu buttons fit twelve actions without overlapping paging or details.
        float[][] menus=new float[12][];
        for(int i=0;i<12;++i) {
            menus[i]=ThorDeckLayout.menu(i);
            check(menus[i][0]>=24 && menus[i][2]<=1216 && menus[i][1]>192 && menus[i][3]<864,"Menu safe bounds");
            for(int j=0;j<i;++j) check(menus[i][0]>=menus[j][2] || menus[j][0]>=menus[i][2] || menus[i][1]>=menus[j][3] || menus[j][1]>=menus[i][3],"Menu buttons do not overlap");
        }
        check(ThorDeckLayout.neighbor(menus,0,1,0)==1,"Menu right chooses adjacent button");
        check(ThorDeckLayout.neighbor(menus,0,0,1)==2,"Menu down follows the next row");
        float[][] mixed={ThorDeckLayout.command(2),ThorDeckLayout.queue(0),ThorDeckLayout.queue(1)};
        check(ThorDeckLayout.neighbor(mixed,0,0,-1)==1,"Production reachable above commands");
        check(ThorDeckLayout.neighbor(new float[0][],0,1,0)==-1,"Empty state has no phantom target");
        System.out.println("Deck bounds, target separation and spatial focus tests passed");
    }
}
