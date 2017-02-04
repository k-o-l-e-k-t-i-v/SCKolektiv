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
			\kof -> "192.168.43.68",
			\joach -> "192.168.43.219",
			\alex -> "192.168.43.128",
			\tester -> "192.168.43.4"
		];
	}

	*new{ |name| ^super.newCopyArgs(name).makeProxy(120).init; }

	*free {
		instance.isNil.if(
			{
				"You are not log into Kolektiv session".postln;
			},{
				"You leaving Kolektiv session".format(name).warn;
				instance.events.exit;
				instance.clean;
			}
		)
	}

	*players { instance.notNil.if( { instance.events.aliveTask; },{ "You are not log into Kolektiv session".postln; }) }

	*version { ^ver; }

	*print { instance.notNil.if( { instance.print; },{ "You are not log into Kolektiv session".postln; })	}

	*tempo {
		instance.notNil.if(
			{ ^"Current tempo is % bpm".format(currentEnvironment[\tempo].clock.tempo*60); },
			{ "You are not log into Kolektiv session".postln; }
		);
	}

	*tempo_ {|bpm|
		currentEnvironment[\tempo].notNil.if({
			currentEnvironment[\tempo].clock.tempo_(bpm/60);
		});

		instance.notNil.if({
			instance.events.clockTempoSet(bpm);
		});
	}

	*historySave {
		(instance.name.asSymbol != \listener).if({
			File.saveDialog (nil, nil,	{|selectedPath|
				var dir;
				var path = (selectedPath.dirname ++ "/");
				var file = "%_%".format(Date.localtime.dayStamp,selectedPath.basename);
				var isFile = PathName.new(selectedPath).isFile;
				var folderFiles = PathName.new(selectedPath.dirname.standardizePath).files;
				var index = 1;
				isFile.if(
					{ dir = selectedPath; },
					{
						folderFiles.do({|oneFile|
							var fileNameNoExtNoNum = PathName(oneFile.fileNameWithoutExtension).noEndNumbers;
							((fileNameNoExtNoNum == file) or: (fileNameNoExtNoNum == ("%_".format(file)))).if({	index = index + 1; })
						});
						(index == 1).if(
							{ dir = (path +/+ "%.%".format(file, "scd")).asString.standardizePath; },
							{ dir = (path +/+ "%_%.%".format(file, index, "scd")).asString.standardizePath;	}
						);
					}
				);
				History.saveCS(dir);
				PathName.tmp = path;
				// openOS(path);
			},nil)
		});
	}

	*historyReplay {
		History.end;

		File.openDialog (nil, { |path|
			instance.notNil.if({
				instance.makeProxy(120);
			});
			Routine({
				Kolektiv(\listener);
				instance.clean;
				5.wait;
				History.clear.loadCS(path).play;
			}).play;
		});
	}

	*historyRestart {
		(instance.name.asSymbol != \listener).if({
			instance.makeProxy(currentEnvironment[\tempo].clock.tempo*60);
			History.end.clear.start;
			History.enter("Kolektiv.tempo_(%);".format(currentEnvironment[\tempo].clock.tempo*60), name.asSymbol);
		});
	}

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
					// thisProcess.openUDPPort(8080);

					this.accounts.do({ |profil|
						(name.asString != profil.key.asString).if({
							net.put(
								profil.key.asSymbol,
								NetAddr(profil.value.asString, NetAddr.langPort)
								// NetAddr(profil.value.asString, 8080)
							);
						});
					});

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

	makeProxy{|newTempo|
		var proxy;
		currentEnvironment.clear.pop;
		proxy = ProxySpace.new(Server.local);
		proxy.makeTempoClock;
		proxy.clock.tempo_(newTempo/60);

		proxy.push(currentEnvironment);
	}

	print {
		// CHECKPRINT
		"\nNAME || %".format(name).postln;
		"Proxy : %".format(currentEnvironment).postln;
		"Tempo : %".format(currentEnvironment[\tempo].clock.tempo).postln;
		"Beats : %".format(currentEnvironment[\tempo].clock.beats).postln;
		net.keys.do({|key|
			"Others || name: %, ip : % ".format(key, net.at(key)).postln;
		});
		OSCdef.allFuncProxies.do({|temp| temp.do({|osc|	osc.postln;	});	});
	}

	cmdPeriod {
		isMyCmdPeriod.if(
			{ events.cmdPeriod; "CMD+. free all players synth".warn; },
			{ isMyCmdPeriod = true; }
		);
	}

	clean {
		CmdPeriod.remove(instance);
		OSCdef.freeAll;
		History.end;
		History.clear;
		events = nil;
		instance = nil;
	}

	initSendMsg {
		events = ();

		events.join = {|event| net.keysValuesDo {|key, target|
			target.sendMsg('/user/join', name.asSymbol);
		}};
		events.aliveTask = {|event| net.keysValuesDo {|key, target|
			target.sendMsg('/user/alive/task', name.asSymbol);
		}};
		events.aliveAnsw = {|event, target|
			net.at(target.asSymbol).sendMsg('/user/alive/answ', name.asSymbol);
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


		events.clockTempoSet = {|event, bpm| net.keysValuesDo {|key, target|
			target.sendMsg('/clock/setTempo/set', name.asSymbol, bpm);
		}};
	}

	initReceiveMsg {

		OSCdef.newMatching(\msg_join, {|msg, time, addr, recvPort|
			var msgType = msg[0];
			var sender = msg[1];
			"Player % has joined to session".format(sender).warn;
			events.aliveAnsw(sender.asSymbol);
			events.clockTempoSet(currentEnvironment[\tempo].clock.tempo*60);

		}, '/user/join', nil).permanent_(true);

		OSCdef.newMatching(\msg_alive_task, {|msg, time, addr, recvPort|
			var msgType = msg[0];
			var sender = msg[1];

			events.aliveAnsw(sender.asSymbol);

		}, '/user/alive/task', nil).permanent_(true);

		OSCdef.newMatching(\msg_alive_answ, {|msg, time, addr, recvPort|
			var msgType = msg[0];
			var sender = msg[1];
			"Player % is also prepared".format(sender).warn;

		}, '/user/alive/answ', nil).permanent_(true);

		OSCdef.newMatching(\msg_exit, {|msg, time, addr, recvPort|
			var msgType = msg[0];
			var sender = msg[1];
			"Player % exit session".format(sender).warn;

		}, '/user/exit', nil).permanent_(true);

		OSCdef.newMatching(\msg_clockTempoSet, {|msg, time, addr, recvPort|
			var msgType = msg[0];
			var sender = msg[1];
			var bpm = msg[2];
			(bpm.asInteger != (currentEnvironment[\tempo].clock.tempo*60).asInteger).if({
				"Kolektiv tempo change by % to % bpm".format(sender, bpm).warn;
				currentEnvironment[\tempo].clock.tempo_(bpm/60);
				History.enter("Kolektiv.tempo_(%);".format(bpm.asInteger), name.asSymbol);
			});

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

				"\n\nCodeExecute from %\n%".format(sender,  code).postln;
				thisProcess.interpreter.interpret(code.asString);
				History.enter(code.asString, sender.asSymbol);
			};
		}, '/code/execute', nil).permanent_(true);
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
