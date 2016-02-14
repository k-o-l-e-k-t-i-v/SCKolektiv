
Scenes{
  classvar index;
  classvar list;
  classvar instance;
  classvar p;


  *new{|proxyspace|
      p = proxyspace;
      
      
  instance.isNil.if(
      instance = this;
     // CmdPeriod.add(this);
    );
          
    index = 1;
    list = List.new;
  }

  *reset{
    list = List.new;
  }

  *save{
      var cnt = 0;

      p.playingProxies.collect{ cnt = cnt + 1; };

      if(cnt>0){

      
      list.add(List.new);
      ("saving scene no.:"+(list.size-1)).postln;
      
      p.playingProxies.postln;

      p.playingProxies.collect{|n|
          var synth = p[n.asSymbol];
          list[list.size-1].add(synth);

      }

  }{ "no playing proxies found".postln};
  }

  *load{|id|
   
      index = id;
      "loading scene no.:"+index;

      p.playingProxies.collect{|n|
          var synth = p[n.asSymbol];
          synth.stop(1.1); 
      };

    list[index].collect{|n|
      n.play();
    }

  }
}
