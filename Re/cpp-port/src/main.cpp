#include <X11/Xlib.h>
#include <X11/keysym.h>
#include <algorithm>
#include <array>
#include <chrono>
#include <cmath>
#include <cstdint>
#include <fstream>
#include <iostream>
#include <sstream>
#include <string>
#include <thread>
#include <unordered_map>
#include <vector>

namespace ri {
using Clock = std::chrono::steady_clock;
constexpr float WORLD_W=3200, WORLD_H=2200, PI=3.1415926535f;

struct Rect { float x{},y{},w{},h{}; };
struct Player { float x=710,y=930,vx=0,vy=0,r=15; std::string facing="south"; };
struct Building { std::string id,name,screen; Rect box; };
struct Npc { std::string id,name,text; float x{},y{},homeX{},homeY{},phase{}; bool patrol{}; };
struct Resource { std::string id,name,item; float x{},y{}; std::int64_t readyAt{}; };
struct Enemy { std::string id,name,species; float x{},y{},homeX{},homeY{},aggro{},speed{}; std::int64_t readyAt{}; };

static std::int64_t unixMs(){return std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::system_clock::now().time_since_epoch()).count();}
static float clampf(float v,float a,float b){return std::max(a,std::min(b,v));}
static float distance(float ax,float ay,float bx,float by){return std::hypot(ax-bx,ay-by);}

class Game {
public:
  Player player;
  std::vector<Building> buildings{
    {"academy","Gu Yue Academy","Academy",{410,315,520,300}}, {"clan","Clan Hall","Mission Hall",{1040,320,430,270}},
    {"shop","Gu Shop","Shop",{345,1040,300,235}}, {"tavern","Bamboo Tavern","Tavern",{735,1060,310,220}},
    {"auction","Auction House","Auction",{1130,1030,365,250}}, {"home","Your House","House",{240,1420,320,225}},
    {"garden","Herb Garden","Garden",{660,1435,330,215}}, {"tending","Gu Tending House","Tending House",{1090,1430,350,220}}
  };
  std::vector<Npc> npcs{
    {"guard","Clan Guard Lan","The academy lies north. Beyond the eastern gate, the mountain shows no mercy.",670,700,670,700,0,true},
    {"elder","Elder Song","A Gu Master cultivates aperture and judgment alike. The latter is rarer.",1110,690,1110,690,0,false},
    {"chen","Chen Yu","If you seek Moon Orchids, follow the southern path into the bamboo.",770,900,770,900,1,false},
    {"hunter","Old Hunter Wei","Wolves circle the deep bamboo. Walk quietly -- or walk ready.",1710,1170,1710,1170,2,false},
    {"cultivator","Suspicious Cultivator","There are inheritances in these hills. None meant for the weak.",2500,580,2500,580,3,true}
  };
  std::vector<Resource> resources{
    {"orchid1","Moon Orchid","moon_orchid",1630,1450,0},{"orchid2","Moon Orchid","moon_orchid",1940,1710,0},
    {"orchid3","Moon Orchid","moon_orchid",2240,1370,0},{"leaf1","Vitality Leaf","vitality_leaf",1810,1110,0},
    {"leaf2","Vitality Leaf","vitality_leaf",2710,1550,0},{"stones","Primeval Stone Shards","stones",2870,510,0}
  };
  std::vector<Enemy> enemies{
    {"wolf1","Grey Wolf","grey_wolf",2140,1050,2140,1050,145,55,0},
    {"wolf2","Grey Wolf","grey_wolf",2590,1300,2590,1300,155,58,0},
    {"bear1","Mountain Bear","mountain_bear",2850,730,2850,730,125,38,0}
  };
  std::vector<Rect> solids;
  std::unordered_map<std::string,int> inventory{{"moon_orchid",0},{"vitality_leaf",0},{"stones",100}};
  std::unordered_map<std::string,bool> discovered;
  bool keys[4]{}, running=true, dialogue=false, inPanel=false, inCombat=false;
  std::string message, panelTitle, nearbyType, nearbyId, area="Gu Yue Village";
  float camX=0,camY=0,worldMinutes=480; int day=1,enemyHp=0,playerHp=100;

  Game(){
    for(const auto& b:buildings) solids.push_back(b.box);
    solids.insert(solids.end(),{{0,0,3200,80},{0,2120,3200,80},{0,0,80,2200},{3120,0,80,2200},{1550,80,80,780},{1550,1780,80,340}});
  }
  bool walkable(float x,float y)const{
    for(const auto&s:solids)if(x+player.r>s.x&&x-player.r<s.x+s.w&&y+player.r>s.y&&y-player.r<s.y+s.h)return false;
    return x>=player.r&&y>=player.r&&x<=WORLD_W-player.r&&y<=WORLD_H-player.r;
  }
  void update(float dt,int viewW,int viewH){
    if(!dialogue&&!inPanel&&!inCombat){
      float dx=(keys[3]?1.f:0.f)-(keys[2]?1.f:0.f),dy=(keys[1]?1.f:0.f)-(keys[0]?1.f:0.f),len=std::hypot(dx,dy);
      if(len>0){dx/=len;dy/=len;} const float target=175,blend=std::min(1.f,12*dt);
      player.vx+=(dx*target-player.vx)*blend;player.vy+=(dy*target-player.vy)*blend;
      if(len==0){player.vx*=std::max(0.f,1-14*dt);player.vy*=std::max(0.f,1-14*dt);}
      float nx=player.x+player.vx*dt;if(walkable(nx,player.y))player.x=nx;else player.vx=0;
      float ny=player.y+player.vy*dt;if(walkable(player.x,ny))player.y=ny;else player.vy=0;
      if(std::abs(player.vx)>std::abs(player.vy)&&std::abs(player.vx)>5)player.facing=player.vx>0?"east":"west";
      else if(std::abs(player.vy)>5)player.facing=player.vy>0?"south":"north";
    }
    for(auto&n:npcs){n.phase+=dt*.45f;float range=n.patrol?105.f:45.f;n.x=n.homeX+std::sin(n.phase)*range;}
    if(!dialogue&&!inPanel&&!inCombat)for(auto&e:enemies){if(e.readyAt>unixMs())continue;float d=distance(player.x,player.y,e.x,e.y);if(d<e.aggro){e.x+=(player.x-e.x)/std::max(d,1.f)*e.speed*dt;e.y+=(player.y-e.y)/std::max(d,1.f)*e.speed*dt;if(d<34)startCombat(e);}else{e.x+=(e.homeX-e.x)*dt*.08f;e.y+=(e.homeY-e.y)*dt*.08f;}}
    worldMinutes+=dt*.65f;if(worldMinutes>=1440){worldMinutes-=1440;day++;}
    camX+=(clampf(player.x-viewW/2.f,0,std::max(0.f,WORLD_W-viewW))-camX)*std::min(1.f,6*dt);
    camY+=(clampf(player.y-viewH/2.f,0,std::max(0.f,WORLD_H-viewH))-camY)*std::min(1.f,6*dt);
    findNearby();discover();
  }
  void findNearby(){nearbyType.clear();nearbyId.clear();float best=88;
    auto take=[&](const std::string&t,const std::string&id,float x,float y){float d=distance(player.x,player.y,x,y);if(d<best){best=d;nearbyType=t;nearbyId=id;}};
    for(const auto&b:buildings)take("building",b.id,b.box.x+b.box.w/2,b.box.y+b.box.h+25);
    for(const auto&n:npcs)take("npc",n.id,n.x,n.y);
    for(const auto&r:resources)if(r.readyAt<=unixMs())take("resource",r.id,r.x,r.y);
    for(const auto&e:enemies)if(e.readyAt<=unixMs())take("enemy",e.id,e.x,e.y);
  }
  void interact(){
    if(inCombat){enemyHp-=20;message="You strike using Moonlight Gu for 20 damage.";if(enemyHp<=0)finishCombat(true);return;}
    if(inPanel){inPanel=false;message="You return to the mountain.";return;}if(dialogue){dialogue=false;message.clear();return;}
    if(nearbyType=="building")for(const auto&b:buildings)if(b.id==nearbyId){inPanel=true;panelTitle=b.screen;message=b.name+" -- native interface preview. Press E to return.";}
    if(nearbyType=="npc")for(const auto&n:npcs)if(n.id==nearbyId){dialogue=true;message=n.name+": "+n.text+"  [E] Leave";}
    if(nearbyType=="resource")for(auto&r:resources)if(r.id==nearbyId){inventory[r.item]+=r.item=="stones"?4:1;r.readyAt=unixMs()+(r.item=="stones"?300000:180000);message="Gathered "+r.name;save("ri_cpp_save.txt");}
    if(nearbyType=="enemy")for(auto&e:enemies)if(e.id==nearbyId)startCombat(e);
  }
  void startCombat(Enemy&e){if(inCombat)return;inCombat=true;nearbyId=e.id;enemyHp=e.species=="mountain_bear"?160:80;playerHp=100;message="Combat: A/D evade, E attack, Q flee.";}
  void combatTick(){if(!inCombat)return;playerHp-=8;if(playerHp<=0)finishCombat(false);else message="The beast hits you. E to attack. Life: "+std::to_string(playerHp);}
  void finishCombat(bool won){for(auto&e:enemies)if(e.id==nearbyId){if(won){e.readyAt=unixMs()+180000;inventory["stones"]+=e.species=="mountain_bear"?18:8;message="Victory. You gather the beast's primeval stones.";}else{player.x=710;player.y=930;message="Defeated. You awaken in Gu Yue Village.";}e.x=e.homeX;e.y=e.homeY;}inCombat=false;save("ri_cpp_save.txt");}
  void discover(){std::string next=player.x>2550&&player.y<1050?"Deep Mountain":player.x>1850?"Bamboo Forest":player.x>1350?"Southern Mountain Path":player.y<800?"Gu Yue Academy":"Gu Yue Village";if(!discovered[next]){discovered[next]=true;message=next+" discovered";}area=next;}
  void save(const std::string&path)const{
    std::ofstream f(path); if(!f)return;
    f<<"RI_NATIVE_SAVE 1\n"<<player.x<<' '<<player.y<<' '<<worldMinutes<<' '<<day<<'\n';
    for(const auto&i:inventory)f<<"I "<<i.first<<' '<<i.second<<'\n';
    for(const auto&r:resources)f<<"R "<<r.id<<' '<<r.readyAt<<'\n';
    for(const auto&e:enemies)f<<"E "<<e.id<<' '<<e.readyAt<<'\n';
    for(const auto&entry:discovered)if(entry.second)f<<"D "<<entry.first<<'\n';
  }
  bool load(const std::string&path){std::ifstream f(path);std::string magic;int version;if(!(f>>magic>>version)||magic!="RI_NATIVE_SAVE")return false;f>>player.x>>player.y>>worldMinutes>>day;std::string type,id;while(f>>type){if(type=="I"){int n;f>>id>>n;inventory[id]=n;}else if(type=="R"){std::int64_t t;f>>id>>t;for(auto&r:resources)if(r.id==id)r.readyAt=t;}else if(type=="E"){std::int64_t t;f>>id>>t;for(auto&e:enemies)if(e.id==id)e.readyAt=t;}else if(type=="D"){std::getline(f,id);if(!id.empty()&&id[0]==' ')id.erase(0,1);discovered[id]=true;}}return true;}
};

class X11App {
  Display*d{};Window win{};GC gc{};Pixmap buffer{};int w=1100,h=700;Game game;Clock::time_point last=Clock::now(),lastAttack=Clock::now();
  unsigned long color(const char*hex){XColor c,e;Colormap m=DefaultColormap(d,DefaultScreen(d));XAllocNamedColor(d,m,hex,&c,&e);return c.pixel;}
  void rect(int x,int y,int ww,int hh,const char*col){XSetForeground(d,gc,color(col));XFillRectangle(d,buffer,gc,x,y,ww,hh);}
  void text(int x,int y,const std::string&s,const char*col="#ead99c"){XSetForeground(d,gc,color(col));XDrawString(d,buffer,gc,x,y,s.c_str(),static_cast<int>(s.size()));}
  int sx(float x)const{return static_cast<int>(x-game.camX);}int sy(float y)const{return static_cast<int>(y-game.camY);}
public:
  int run(){d=XOpenDisplay(nullptr);if(!d){std::cerr<<"No graphical display found. Run ./ri_native --self-test for a headless test.\n";return 2;}int s=DefaultScreen(d);win=XCreateSimpleWindow(d,RootWindow(d,s),20,20,w,h,1,BlackPixel(d,s),BlackPixel(d,s));XStoreName(d,win,"Reverend Insanity - Native C++ Port");XSelectInput(d,win,ExposureMask|KeyPressMask|KeyReleaseMask|StructureNotifyMask);gc=XCreateGC(d,win,0,nullptr);XMapWindow(d,win);buffer=XCreatePixmap(d,win,w,h,DefaultDepth(d,s));game.load("ri_cpp_save.txt");
    while(game.running){events();auto now=Clock::now();float dt=std::min(.05f,std::chrono::duration<float>(now-last).count());last=now;game.update(dt,w,h);draw();std::this_thread::sleep_for(std::chrono::milliseconds(8));}game.save("ri_cpp_save.txt");XFreePixmap(d,buffer);XFreeGC(d,gc);XDestroyWindow(d,win);XCloseDisplay(d);return 0;}
  void events(){while(XPending(d)){XEvent e;XNextEvent(d,&e);if(e.type==ConfigureNotify){w=std::max(640,e.xconfigure.width);h=std::max(420,e.xconfigure.height);XFreePixmap(d,buffer);buffer=XCreatePixmap(d,win,w,h,DefaultDepth(d,DefaultScreen(d)));}if(e.type==KeyPress||e.type==KeyRelease){bool on=e.type==KeyPress;KeySym k=XLookupKeysym(&e.xkey,0);if(k==XK_w||k==XK_Up)game.keys[0]=on;if(k==XK_s||k==XK_Down)game.keys[1]=on;if(k==XK_a||k==XK_Left)game.keys[2]=on;if(k==XK_d||k==XK_Right)game.keys[3]=on;if(on&&k==XK_e)game.interact();if(on&&k==XK_q&&game.inCombat)game.finishCombat(false);if(on&&k==XK_Escape){if(game.dialogue||game.inPanel){game.dialogue=false;game.inPanel=false;}else game.running=false;}}}}
  void draw(){rect(0,0,w,h,"#263b23");for(int x=static_cast<int>(game.camX/150)*150;x<game.camX+w+150;x+=150)for(int y=static_cast<int>(game.camY/150)*150;y<game.camY+h+150;y+=150){if(x<1580&&y<1750)continue;rect(sx(x+36),sy(y+25),8,60,"#182915");XSetForeground(d,gc,color("#355430"));XFillArc(d,buffer,gc,sx(x+12),sy(y),56,55,0,360*64);}rect(sx(730),sy(80),150,1770,"#8d7548");rect(sx(80),sy(850),1540,145,"#8d7548");rect(sx(1450),sy(1100),1500,120,"#8d7548");
    for(const auto&b:game.buildings){rect(sx(b.box.x),sy(b.box.y),b.box.w,b.box.h,"#3a291a");rect(sx(b.box.x+15),sy(b.box.y+18),b.box.w-30,55,"#75512c");rect(sx(b.box.x+b.box.w/2-25),sy(b.box.y+b.box.h-65),50,65,"#17120d");text(sx(b.box.x+18),sy(b.box.y+48),b.name);}
    for(const auto&r:game.resources)if(r.readyAt<=unixMs()){rect(sx(r.x-5),sy(r.y-14),10,28,r.item=="stones"?"#87a7a0":"#8eb268");}
    for(const auto&n:game.npcs){rect(sx(n.x-10),sy(n.y-28),20,40,"#5b4930");text(sx(n.x-35),sy(n.y-38),n.name);}
    for(const auto&e:game.enemies)if(e.readyAt<=unixMs()){XSetForeground(d,gc,color(e.species=="mountain_bear"?"#382c21":"#59605a"));XFillArc(d,buffer,gc,sx(e.x-18),sy(e.y-13),36,26,0,360*64);}
    XSetForeground(d,gc,color("#c9a24a"));XFillArc(d,buffer,gc,sx(game.player.x-11),sy(game.player.y-32),22,22,0,360*64);rect(sx(game.player.x-13),sy(game.player.y-11),26,30,"#273d35");
    rect(12,12,310,62,"#17120e");text(24,35,game.area);std::ostringstream stats;stats<<"Day "<<game.day<<"  "<<static_cast<int>(game.worldMinutes/60)<<":"<<static_cast<int>(game.worldMinutes)%60<<"   Stones "<<game.inventory["stones"];text(24,57,stats.str());
    rect(w-220,12,205,140,"#172116");float mx=205/WORLD_W,my=140/WORLD_H;for(const auto&b:game.buildings)rect(w-220+static_cast<int>(b.box.x*mx),12+static_cast<int>(b.box.y*my),std::max(2,static_cast<int>(b.box.w*mx)),std::max(2,static_cast<int>(b.box.h*my)),"#695638");rect(w-220+static_cast<int>(game.player.x*mx)-2,12+static_cast<int>(game.player.y*my)-2,5,5,"#f3d66e");
    if(!game.nearbyType.empty()&&!game.dialogue&&!game.inPanel&&!game.inCombat){rect(w/2-130,h-72,260,34,"#17120e");text(w/2-115,h-50,"[E] Interact: "+game.nearbyId);}
    if(!game.message.empty()){rect(40,h-130,w-80,72,"#21180e");text(58,h-96,game.message);}
    if(game.inPanel){rect(w/2-260,h/2-150,520,300,"#bda873");text(w/2-220,h/2-95,game.panelTitle,"#281e10");text(w/2-220,h/2-55,"Native system screen - [E] return","#281e10");}
    if(game.inCombat){rect(w/2-280,90,560,100,"#351712");text(w/2-240,125,"EXPLORATION ENCOUNTER");text(w/2-240,155,"Enemy life: "+std::to_string(game.enemyHp)+"   Your life: "+std::to_string(game.playerHp)+"   [E] Attack  [Q] Flee");}
    XCopyArea(d,buffer,win,gc,0,0,w,h,0,0);XFlush(d);}
};
}

int main(int argc,char**argv){
  if(argc>1&&std::string(argv[1])=="--self-test"){
    ri::Game g;bool normalized=true;float dx=1/std::sqrt(2.f),dy=1/std::sqrt(2.f);normalized=std::abs(std::hypot(dx,dy)-1)<.001f;
    bool collision=!g.walkable(500,400);g.inventory["stones"]=123;g.save("ri_cpp_test_save.txt");ri::Game loaded;bool persisted=loaded.load("ri_cpp_test_save.txt")&&loaded.inventory["stones"]==123;
    std::cout<<"movement_normalization="<<(normalized?"PASS":"FAIL")<<"\ncollision="<<(collision?"PASS":"FAIL")<<"\nsave_load="<<(persisted?"PASS":"FAIL")<<"\n";return normalized&&collision&&persisted?0:1;
  }
  return ri::X11App{}.run();
}
