
Scenes{
	classvar index;
	classvar list;
	classvar sources;
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
		sources = List.new;
	}

	*reset{
		list = List.new;
		sources = List.new;
	}

	*save{
		var cnt = 0;

		p.playingProxies.collect{ cnt = cnt + 1; };

		if(cnt>0){


			list.add(List.new);
			sources.add(List.new);
			("saving scene no.:"+(list.size-1)).postln;

			p.playingProxies.postln;

			p.playingProxies.collect{|n|
				var synth = p[n.asSymbol];
				list[list.size-1].add(synth);
				sources[sources.size-1].add(synth.source.asCompileString)
			}

		}{ "no playing proxies found".postln};
	}

	*load{|id|

		var idx = 0;
		index = id;
		"loading scene no.:"+index;

		p.playingProxies.collect{|n|
			var synth = p[n.asSymbol];
			synth.stop(1.1);
		};

		list[index].collect{|n|
			n = sources[index][idx].interpret;
			sources[index][idx].postln;
			n.play();
			idx=idx+1;
		}

	}
}
