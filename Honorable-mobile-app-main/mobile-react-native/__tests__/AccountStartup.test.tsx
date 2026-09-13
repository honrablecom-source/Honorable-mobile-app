import React from 'react';
import Renderer,{act} from 'react-test-renderer';
import {MemoryPassProvider,useMemoryPass} from '../src/passes/MemoryPassContext';
const mockRestore=jest.fn(),mockSignIn=jest.fn(),mockSignOut=jest.fn();
jest.mock('../src/native/HonorableNative',()=>({honorableNative:{getAccountConfiguration:async()=>({googleConfigured:true,apiUrl:'https://account.test'}),restoreAccountSession:()=>mockRestore(),signInAccountWithGoogle:()=>mockSignIn(),signOutAccountSession:()=>mockSignOut()}}));
let latest:ReturnType<typeof useMemoryPass>;
function Probe(){latest=useMemoryPass();return null}
const account={accountId:'opaque',balance:50,subscription:{status:'NONE'},transactions:[],creditsExpire:false};
test('returning account waits in splash then bypasses welcome and signout clears account',async()=>{
 let resolve!:(value:unknown)=>void;mockRestore.mockImplementation(()=>new Promise(r=>{resolve=r}));let tree:Renderer.ReactTestRenderer;
 await act(async()=>{tree=Renderer.create(<MemoryPassProvider><Probe/></MemoryPassProvider>)});
 expect(latest.status).toBe('restoring');expect(latest.signedIn).toBe(false);
 await act(async()=>{resolve({status:'online',account})});expect(latest.signedIn).toBe(true);expect(latest.account?.balance).toBe(50);
 mockSignOut.mockResolvedValue({signedOut:true});await act(async()=>{await latest.signOut()});expect(latest.status).toBe('welcome');expect(latest.account).toBeUndefined();await act(async()=>tree!.unmount());
});
test('first launch uses welcome, Google establishes account, offline never grants server spending authority',async()=>{
 mockRestore.mockResolvedValue({status:'welcome'});let tree:Renderer.ReactTestRenderer;await act(async()=>{tree=Renderer.create(<MemoryPassProvider><Probe/></MemoryPassProvider>)});expect(latest.status).toBe('welcome');mockSignIn.mockResolvedValue({status:'online',account});await act(async()=>latest.signInGoogle());expect(latest.account?.balance).toBe(50);mockRestore.mockResolvedValue({status:'offline',account});await act(async()=>latest.refresh());expect(latest.signedIn).toBe(true);expect(latest.connected).toBe(false);await expect(latest.charge('SERAN_V1','request')).rejects.toThrow('Reconnect');await act(async()=>tree!.unmount());
});
