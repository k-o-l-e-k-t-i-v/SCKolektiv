Kolektiv {
	classvar ver = 0.76;
	classvar serverMemory = 5529600;
	classvar doc;
	classvar isOpenDoc;

	classvar instance;
	var <name, net, group;
	var <events;

	var isMyCmdPeriod = true;

	accounts{
		^[
			\kof -> "10.8.0.2",
			\joach -> "10.8.0.3",
			\alex -> "10.8.0.6",
			\tester -> "10.8.0.4"
		];
	}

	*new{ |name| ^super.newCopyArgs(name).makeProxy.init; }

	*free {
		instance.isNil.if({
			"You arn not log in to Kolektiv session".postln;
		},{
			"You leaving Kolektiv session".format(name).warn;
			instance.events.exit;
			instance.clean;
		})
	}

	*players { instance.events.join; }

	*version { instance.print; ^ver; }

	*print { instance.isNil.if( { "You are not log in to Kolektiv session".postln; },{	instance.print; })	}

	*tempo { ^"Current tempo is % bpm".format(currentEnvironment[\tempo].clock.tempo*60); }

	*tempo_ {|bpm|
		currentEnvironment[\tempo].clock.tempo_(bpm/60);
		instance.events.clockTempo_(bpm);
	}

	*historySave {
		var dir = (Kolektiv.filenameSymbol.asString.dirname +/+ "History").standardizePath;
		var file = "KolektivHistory_%.scd".format(Date.localtime.stamp);
		History.end;
		History.saveCS(dir +/+ file);
	}

	*historyReplay {
		File.openDialog (nil, { |path|
			instance.notNil.if({ instance.clean; });
			Kolektiv(\listener);
			Server.local.waitForBoot({
				History.clear.loadCS(path).play;
			});
		});
	}

	*historyRestart { History.end.clear.start; }

	init {
		instance.isNil.if({
			instance = this;
			CmdPeriod.add(this);

			(NetAddr.langPort != 57120).if({
				"Kolektiv not booting on port 57120 [current boot port %]".format(NetAddr.langPort).warn;
				instance = nil;
			}, {
				"Kolektiv(%) instance is running now [ver %]".format(name, ver).warn;

				Server.local.options.memSize = serverMemory;
				Server.internal.options.memSize = serverMemory;
				Server.local.waitForBoot({

					net = Dictionary.new;
					group = Dictionary.new;

					// thisProcess.openUDPPort(8080);

					this.accounts.do({ |profil|
						var id = profil.value.asString.replace(".","");
						var profilGroup = Group.basicNew(Server.local, id.asInteger);
						// CmdPeriod.add(profilGroup);
						profilGroup.isPlaying_(true);
						profilGroup.isRunning_(true);
						profilGroup.postln;
						Server.local.sendBundle(nil, profilGroup.newMsg;);
						group.put(
							profil.key.asSymbol,
							profilGroup.asGroup
						);

						(name.asString != profil.key.asString).if({
							net.put(
								profil.key.asSymbol,
								NetAddr(profil.value.asString, NetAddr.langPort)
								// NetAddr(profil.value.asString, 8080)
							);
						});
					});
					// group.do({|key| key.postln; });
					(group.at(\kof).asString).warn;
					(group.at(\joach).asString).warn;
					currentEnvironment.group = group.at(name.asSymbol);

					isOpenDoc = false;

					this.initHistory;
					this.initReceiveMsg;
					this.initSendMsg;

					events.join;
					// events.clockTime(clock.beats);
					// ShutDown.add({ this.free; });
				}
				);
			});
		}, {
			this.clean;
			Kolektiv(name);
		});
	}

	makeProxy{
		var proxy;
		currentEnvironment.clear.pop;
		proxy = ProxySpace.new(Server.local);
		proxy.makeTempoClock;
		proxy.clock.tempo_(120/60);

		proxy.push(currentEnvironment);
	}

	print {

		// CHECKPRINT
		"\nNAME || %".format(name).postln;
		"Proxy : %".format(currentEnvironment).postln;
		"Tempo : %".format(currentEnvironment[\tempo].clock.tempo).postln;
		"Beats : %".format(currentEnvironment[\tempo].clock.beats).postln;
		// events.clockTime(clock.beats);
		net.keys.do({|key|
			"Others || name: %, ip : % ".format(key, net.at(key)).postln;
		});
		OSCdef.allFuncProxies.do({|temp| temp.do({|osc|	osc.postln;	});	});
	}

	cmdPeriod {
		isMyCmdPeriod.if( { events.cmdPeriod; } , { isMyCmdPeriod = true; "CMD+. free all players synth".warn; } );
	}

	clean {
		CmdPeriod.remove(instance);
		OSCdef.freeAll;
		History.end;
		History.clear;
		instance = nil;
	}

	initSendMsg {
		events = ();

		events.join = {|event| net.keysValuesDo {|key, target|
			target.sendMsg('/user/join', name.asSymbol);
		}};
		events.alive = {|event, target, clockTime|
			net.at(target.asSymbol).sendMsg('/user/alive', name.asSymbol, clockTime);
		};
		events.exit = {|event| net.keysValuesDo {|key, target|
			target.sendMsg('/user/exit', name.asSymbol);
		}};


		events.cmdPeriod = {|event| net.keysValuesDo {|key, target|
			target.sendMsg('/code/cmdPeriod', name);
		}};
		events.execute = {|event, code|	net.keysValuesDo {|key, target|
			target.sendMsg('/code/execute', name, code);
		}};
		events.change = { |event, cursorIndex, deleteIndex, changedTxt, docTxt| net.keysValuesDo {|key, target|
			target.sendMsg('/code/change', name, cursorIndex, deleteIndex, changedTxt, docTxt);
		}};


		events.clockTime = {|event, clockTime| net.keysValuesDo {|key, target|
			target.sendMsg('/clock/latency', name.asSymbol, clockTime);
		}};
		events.clockTimeAnswer = {|event, target, clockTime|
			net.at(target.asSymbol).sendMsg('/clock/latencyAnswer', name.asSymbol, clockTime);
		};
		events.clockTempo_ = {|event, bpm| net.keysValuesDo {|key, target|
			target.sendMsg('/clock/setTempo/set', name.asSymbol, bpm);
		}};
	}

	initReceiveMsg {

		OSCdef.newMatching(\msg_join, {|msg, time, addr, recvPort|
			var msgType = msg[0];
			var sender = msg[1];
			"Player % has joined to session".format(sender).warn;

			events.alive(sender.asSymbol);
			// events.alive(sender.asSymbol, clock.beats);

		}, '/user/join', nil).permanent_(true);

		OSCdef.newMatching(\msg_alive, {|msg, time, addr, recvPort|
			var msgType = msg[0];
			var sender = msg[1];
			// var senderClock = msg[2];
			"Player % is also prepared".format(sender).warn;
			// clock.beats_(senderClock);

		}, '/user/alive', nil).permanent_(true);

		OSCdef.newMatching(\msg_exit, {|msg, time, addr, recvPort|
			var msgType = msg[0];
			var sender = msg[1];
			"Player % exit session".format(sender).warn;

		}, '/user/exit', nil).permanent_(true);

		/*
		OSCdef.newMatching(\msg_clockLatency, {|msg, time, addr, recvPort|
		var msgType = msg[0];
		var sender = msg[1];
		var senderClock = msg[2].asFloat;
		"Player % check letency [%]".format(sender, (clock.beats.asFloat - senderClock)).postln;

		events.clockTimeAnswer(sender, clock.beats);
		}, '/clock/latency', nil).permanent_(true);

		OSCdef.newMatching(\msg_clockLatencyAnswer, {|msg, time, addr, recvPort|
		var msgType = msg[0];
		var sender = msg[1];
		var senderClock = msg[2].asFloat;
		"Answer on latency check from % : %".format(sender, (clock.beats.asFloat - senderClock)).postln;

		}, '/clock/latencyAnswer', nil).permanent_(true);
		*/
		OSCdef.newMatching(\msg_clockTempoSet, {|msg, time, addr, recvPort|
			var msgType = msg[0];
			var sender = msg[1];
			var bpm = msg[2];
			"Kolektiv tempo change by % to % bpm".format(sender, bpm).warn;
			// proxy.at(\tempo).clock.tempo = bpm/60;
			currentEnvironment[\tempo].clock.tempo_(bpm/60);

		}, '/clock/setTempo/set', nil).permanent_(true);

		OSCdef.newMatching(\msg_kill, {|msg, time, addr, recvPort|
			var msgType = msg[0];
			var sender = msg[1];
			"Player % call cmdPeriod".format(sender).warn;
			isMyCmdPeriod = false;
			CmdPeriod.run;
		}, '/code/cmdPeriod', nil).permanent_(true);

		OSCdef.newMatching(\msg_change, {|msg, time, addr, recvPort|

			var msgType = msg[0];
			var sender = msg[1];
			var insertCursorIndex = msg[2];
			var deleteIndex = msg[3];
			var changedTxt = msg[4];
			var code = msg[5];

			var currentCursorIndex = doc.selectionStart;

			doc.text = code.asString;

			if (deleteIndex <= 0)
			{
				if(currentCursorIndex >= insertCursorIndex)
				{ doc.selectRange(currentCursorIndex + changedTxt.asString.size); }
				{ doc.selectRange(currentCursorIndex); };
			} {
				if(currentCursorIndex > insertCursorIndex)
				{ doc.selectRange(currentCursorIndex - deleteIndex); }
				{ doc.selectRange(currentCursorIndex); };
			};

		}, '/code/change', nil).permanent_(true);

		OSCdef.newMatching(\msg_execute, {|msg, time, addr, recvPort|
			var msgType = msg[0];
			var sender = msg[1];
			var code = msg[2];
			if(code.asString.find("Kolektiv").isNil)
			{
				code = this.blockCode(code);
				// this.putNodeToGroup;
				// currentEnvironment.group = profilGroup.nodeID;

				"\n\nCodeExecute from %\n%".format(sender,  code).postln;
				thisProcess.interpreter.interpret(code.asString);
				History.enter(code.asString, sender.asSymbol);
			};
		}, '/code/execute', nil).permanent_(true);
	}
	putNodeToGroup{
		Server.local.nextNodeID.postln;
	}

	blockCode {|code|

		".plot".matchRegexp(code.asString).if({ code = code.asString.replace(".plot",""); });
		^code;

		/*
		(
		var code = "Kolektiv(aaa)";
		var txt = "Kolektiv*";
		var find = txt.matchRegexp(code).postln;

		var answ;
		find.if({ answ = code.replace(".plot",""); });
		answ.postln;
		)*/
	}

	initHistory {

		History.localOff;
		History.clear;
		History.start;
		History.forwardFunc = { |code|
			(name.asSymbol != \listener).if({
				(group.at(name.asSymbol).asString).warn;
				(group.at(name.asSymbol).asGroup).postln;
				(group.at(name.asSymbol).nodeID).postcs;
				currentEnvironment.group = group.at(name.asSymbol);//.nodeID.asString;
				"g - %".format(currentEnvironment.group).postln;
				// group.at(\joach).postln;
				// (group.at(name.asSymbol).asString).warn;
				History.enter(code.asString, name.asSymbol);
				events.execute(code.asString);
			},{
				"You are log like Kolektiv(%) now. Log first by another name".format(name).warn;
			});
		};
		(name != \listener).if({ History.enter("Kolektiv.tempo_(120);", name.asSymbol); });
	}

	initDocument { |isShared|

		if(isOpenDoc != true)
		{
			if(isShared)
			{
				doc = Document.new("Kolektiv sharedDoc");

				doc.textChangedAction = {arg ...args;

					var cursorIndex = args[1];
					var deleteIndex = args[2];
					var changedTxt = args[3];

					events.change(cursorIndex, deleteIndex, changedTxt, doc.text);
				};
			} {
				doc = Document.new("KolektivDoc %".format(name));
			};

			isOpenDoc = true;
		} {
			"KolektivDoc has opened already".postln;
		};

		doc.onClose = { isOpenDoc = false };
	}

}
