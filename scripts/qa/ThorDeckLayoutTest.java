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
        float[][] mixed={ThorDeckLayout.command(2),ThorDeckLayout.queue(0),ThorDeckLayout.queue(1)};
        check(ThorDeckLayout.neighbor(mixed,0,0,-1)==1,"Production reachable above commands");
        check(ThorDeckLayout.neighbor(new float[0][],0,1,0)==-1,"Empty state has no phantom target");
        System.out.println("Deck bounds, target separation and spatial focus tests passed");
    }
}
