<?php

namespace App\Http\Controllers;

use App\Template;
use Illuminate\Http\Request;
use App\User;
use Illuminate\Support\Facades\Auth;
use Illuminate\Support\Facades\File;
use Illuminate\Support\Facades\Storage;
use Illuminate\Contracts\Filesystem\FileNotFoundException; 


use Symfony\Component\Process\Process;
use Symfony\Component\Process\Exception\ProcessFailedException;

class TemplateController extends Controller
{
    /**
     * Display a listing of the resource.
     *
     * @return \Illuminate\Http\Response
     */
    public function index()
    {
        //
    }

    /**
     * Show the form for creating a new resource.
     *
     * @return \Illuminate\Http\Response
     */
    public function create($id)
    {
        $user = User::findOrFail($id);
        return view('templates.create', [
            'user' => $user
        ]);
    }

    /**
     * Show the graphical modeller
     * 
     *  @return View
     */
    public function createWithModeler()
    {
        return view('templates.modeler');
    }

    /**
     * Store a newly created resource in storage.
     *
     * @param  \Illuminate\Http\Request  $request
     * @return \Illuminate\Http\Response
     */
    public function validateTemplate(Request $request)
    {
        $base64_template = base64_encode(file_get_contents($request->yaml_template));

        // $filename = Auth::id()."/".uniqid()."/".$request->yaml_template->getClientOriginalName();
        // Storage::disk('public')->put($filename, $request->yaml_template->get());
        // $command = "python ./json4tosca-parser/tosca_parser.py --template-file=".str_replace(env('APP_URL'), ".", Storage::disk('public')->url($filename));
        // $result = shell_exec($command);
        // $process = new Process("python3 ./json4tosca-parser/tosca_parser.py --template-file=".str_replace(env('APP_URL'), ".", Storage::disk('public')->url($filename)));

        $process = new Process("python3 ./json4tosca-parser/tosca_parser.py --template-file=".$base64_template);
        $process->run();
        $ouput_json = $process->getOutput();
        
        if(strcmp($ouput_json,"") == 0)
        {
            return redirect()->back()->with('templateIsValid', FALSE)->with("error", "Invalid template submitted")->withInput();
        }
        else
        {
            return redirect()->back()->with('templateIsValid', TRUE)->with('json_graph', $ouput_json)->with('yaml_template', $base64_template)->withInput();
        }
        // try {

        //     $data = File::get('output.json');
        //     dd($data);
        //     $request->json_graph = $data;
        //     unlink(public_path()."/output.json");
            
        //     return redirect()->back()->with('templateIsValid', TRUE)->with('json_graph', $data)->with('yaml_template', $filename)->withInput();
        // }
        // catch (FileNotFoundException $e)
        // {
        //     return redirect()->back()->with('templateIsValid', FALSE)->with("error", "Invalid template submitted")->withInput();
        // }
        
    }

    /**
     * Store a newly created resource in storage.
     *
     * @param  \Illuminate\Http\Request  $request
     * @return \Illuminate\Http\Response
     */
    public function store(Request $request)
    {        
        $template = Template::create([
            'name' => $request->name, 
            'user_id' => Auth::id(), 
            'yaml_template' => $request->yaml_template, 
            'json_graph' => $request->json_graph
        ]);
        return redirect('home');
    }

    /**
     * Display the specified resource.
     *
     * @param  \App\Template  $template
     * @return \Illuminate\Http\Response
     */
    public function show(User $user, Template $template)
    {
        if (strcmp($template->process_id,"")==0) $template_info = '';
        else {
	    /********* TO DO: AGGIORNARE BPMN ENDPOINT  *********/
            //$endpoint = env("BPMN_ENGINE", "");
	    $endpoint = $template->bpmn_endpoint;

            // TODO: cycle directly on history with size > 100 so that you find all DUs with their state
            $curl = curl_init();
        
            curl_setopt_array($curl, array(
                CURLOPT_URL => $endpoint."/flowable-rest/service/history/historic-process-instances?superProcessInstanceId=".$template->process_id."&includeProcessVariables=true&size=1000",
                CURLOPT_RETURNTRANSFER => true,
                CURLOPT_ENCODING => "",
                CURLOPT_TIMEOUT => 30000,
                CURLOPT_USERPWD => "rest-admin:test",
                CURLOPT_HTTP_VERSION => CURL_HTTP_VERSION_1_1,
                CURLOPT_CUSTOMREQUEST => "GET",
                CURLOPT_HTTPHEADER => array(
                    // Set Here Your Requested Headers
                    'Content-Type: application/json',
                ),
            ));
            $response = curl_exec($curl);
            $err = curl_error($curl);
            curl_close($curl);
            if ($err) {
                $template_info = "cURL Error #:" . $err;
            } else {
                $template_info = array();
                $response = json_decode($response);
                $json_graph = json_decode($template->json_graph);
                foreach ($json_graph as $node)
                {
                    $nodename = $node->name;
                    $template_info[$nodename] = "WAITING";
                    if ($response->total == 0)
                    {
                        $template_info[$nodename] = "DELETED";
                        continue; 
                    } 
                    foreach($response->data as $process)
                    {
                        foreach ($process->variables as $variable)
                        {
                            if (strcmp($variable->name,"body") == 0)
                            {
                                $response_node = json_decode($variable->value);
                                if(strcmp($node->name,$response_node->name) == 0)
                                {
                                    $template_info[$nodename] = "IN PROGRESS";
                                    foreach ($process->variables as $variable)
                                    {
					if(strcmp($node->type, "du") == 0) {
                                                if(strcmp($variable->name,"configureDeploymentUnitStatus") == 0)
                                                {
                                                    if (strcmp($variable->value,"ok") == 0)
                                                    {
                                                        $template_info[$nodename] = "COMPLETED";
                                                        break;
                                                    }
                                                }
                                                elseif(strcmp($variable->name,"createDeploymentUnitResponseStatusCode") == 0)
                                                {
                                                    if ($variable->value == 500)
                                                    {
                                                        $template_info[$nodename] = "FAILED";
                                                    break;
                                                    }
                                                }
                                                elseif(strcmp($variable->name,"checkDeploymentUnitResponseStatusCode") == 0)
                                                {
                                                    if ($variable->value == 500)
                                                    {
                                                        $template_info[$nodename] = "FAILED";
                                                        break;
                                                    }
                                                }
					}
					elseif(strcmp($node->type, "resource") == 0)
					{
                                                if(strcmp($variable->name,"createResourceResponseStatusCode") == 0)
                                                {
                                                    if ($variable->value == 500)
                                                    {
                                                        $template_info[$nodename] = "FAILED";
                                                        break;
                                                    }
                                                }
                                                elseif(strcmp($variable->name,"resourceStatus") == 0)
                                                {
                                                    if (strcmp($variable->value,"ok") == 0)
                                                    {
                                                        $template_info[$nodename] = "COMPLETED";
                                                        break;
                                                    }
                                                    elseif (strcmp($variable->value,"error") == 0)
                                                    {
                                                        $template_info[$nodename] = "FAILED";
                                                        break;
                                                    }
                                                }
					}
					elseif(strcmp($node->type, "package") == 0)
					{
                                                if(strcmp($variable->name,"createPackageResponseStatusCode") == 0)
                                                {
                                                    if ($variable->value == 500)
                                                    {
                                                        $template_info[$nodename] = "FAILED";
                                                        break;
                                                    }
                                                }
                                                elseif(strcmp($variable->name,"configurePackageResponseStatusCode") == 0)
                                                {
                                                    if ($variable->value == 500)
                                                    {
                                                        $template_info[$nodename] = "FAILED";
                                                        break;
                                                    }
                                                }
                                                elseif(strcmp($variable->name,"startPackageStatus") == 0)
                                                {
                                                    if (strcmp($variable->value,"ok") == 0)
                                                    {
                                                        $template_info[$nodename] = "COMPLETED";
                                                        break;
                                                    }
						}
					}
                                    }
                                    break;
                                }
                                break;
                            }
                        } 
                    }

                }
            }
        }
        return view('template', [
            'template' => $template,
            'template_info' => $template_info
        ]);
    }

    /**
     * Show the form for editing the specified resource.
     *
     * @param  \App\Template  $template
     * @return \Illuminate\Http\Response
     */
    public function edit(Template $template)
    {
        //
    }

    /**
     * Update the specified resource in storage.
     *
     * @param  \Illuminate\Http\Request  $request
     * @param  \App\Template  $template
     * @return \Illuminate\Http\Response
     */
    public function update(Request $request, Template $template)
    {
        //
    }

    /**
     * Remove the specified resource from storage.
     *
     * @param  \App\Template  $template
     * @return \Illuminate\Http\Response
     */
    public function destroy(User $user, Template $template)
    {
        $template->delete();
        return redirect('home');
    }
}



// $curl = curl_init();

// curl_setopt_array($curl, array(
//     CURLOPT_URL => $endpoint."/flowable-rest/service/runtime/process-instances?superProcessInstanceId=".$template->process_id."&includeProcessVariables=true&size=1000",
//     CURLOPT_RETURNTRANSFER => true,
//     CURLOPT_ENCODING => "",
//     CURLOPT_TIMEOUT => 30000,
//     CURLOPT_USERPWD => "rest-admin:test",
//     CURLOPT_HTTP_VERSION => CURL_HTTP_VERSION_1_1,
//     CURLOPT_CUSTOMREQUEST => "GET",
//     CURLOPT_HTTPHEADER => array(
//         // Set Here Your Requesred Headers
//         'Content-Type: application/json',
//     ),
// ));
// $response = curl_exec($curl);
// $err = curl_error($curl);
// curl_close($curl);
// if ($err) {
//     $template_info = "cURL Error #:" . $err;
// } else {
//     $response = json_decode($response);
//     if ($response->total == 0)
//     {
//         $curl = curl_init();
        
//         curl_setopt_array($curl, array(
//             CURLOPT_URL => $endpoint."/flowable-rest/service/history/historic-process-instances?superProcessInstanceId=".$template->process_id."&includeProcessVariables=true&size=1000",
//             CURLOPT_RETURNTRANSFER => true,
//             CURLOPT_ENCODING => "",
//             CURLOPT_TIMEOUT => 30000,
//             CURLOPT_USERPWD => "rest-admin:test",
//             CURLOPT_HTTP_VERSION => CURL_HTTP_VERSION_1_1,
//             CURLOPT_CUSTOMREQUEST => "GET",
//             CURLOPT_HTTPHEADER => array(
//                 // Set Here Your Requesred Headers
//                 'Content-Type: application/json',
//             ),
//         ));
//         $response = curl_exec($curl);
//         $err = curl_error($curl);
//         curl_close($curl);

//         $response = json_decode($response);
//         $template_info = "DEPLOYMENT STATUS: COMPLETE\n";  
//         $hasEnded = FALSE;
//         foreach ($response->data as $process)
//         {
//             foreach ($process->variables as $variable)
//             {
//                 if(strcmp($variable->name,"checkDeploymentUnitStatus") == 0)
//                 {
//                     $hasEnded = TRUE;
//                     foreach ($process->variables as $variable_x)
//                     {
//                         if (strcmp($variable_x->name,"body") == 0)
//                         {
//                             $node = json_decode($variable_x->value);
//                             break;
//                         }
//                     }
//                     if (strcmp($variable->value,"ok") == 0)
//                         $template_info = $template_info.$node->name.": COMPLETED\n";
//                     else 
//                     {
//                         $template_info = $template_info.$node->name.": FAILED\n";
//                         if($process->deleteReason)
//                             $template_info = $template_info."Failure reason: ".$process->deleteReason."\n";
//                         else
//                             foreach ($process->variables as $variable)
//                             {
//                                 if (strcmp($variable->name,"checkDeploymentUnitResponse") == 0)
//                                 {
//                                     $template_info = $template_info."Failure reason: ".$variable->value->status."\n";
//                                     break;
//                                 }
//                             }
//                     }
//                     break;
//                 }
//             }
//         }
//         if(!$hasEnded)
//         {
//             $template_info = "DEPLOYMENT STATUS: FAILED";  
//         }
//     } 
//     else 
//     {
//         $template_info = "DEPLOYMENT STATUS: IN PROGRESS\n";
//         $json_graph = json_decode($template->json_graph);
//         foreach ($json_graph as $node)
//         {
//             $found = FALSE;
//             foreach ($response->data as $process)
//             {
//                 if (strcmp($process->name,$node->name) == 0)
//                 {
//                     $found = TRUE;
//                     $WIP = FALSE;
//                     foreach ($process->variables as $variable)
//                     {
//                         if(strcmp($variable->name,"checkDeploymentUnitStatus") == 0)
//                         {
//                             if (strcmp($variable->value,"wip") == 0)
//                             {
//                                 $WIP = TRUE;
//                                 $template_info = $template_info.$node->name.": IN PROGRESS\n";
//                                 break;
//                             }
//                         }
//                     }
//                     if(!$WIP)
//                     {
//                         $CREATING = FALSE;
//                         foreach ($process->variables as $variable)
//                         {
//                             if(strcmp($variable->name,"createDeploymentUnitResponseStatusCode") == 0)
//                             {
//                                 if ($variable->value == 202)
//                                 {
//                                     $CREATING = TRUE;
//                                     $template_info = $template_info.$node->name.": CREATING\n";
//                                     break;
//                                 }
//                             }
//                         }
//                         if(!$CREATING) $template_info = $template_info.$node->name.": WAITING\n";
//                     } 
//                 }
//             } 
//             if(!$found)
//             {
//                 $curl = curl_init();
        
//                 curl_setopt_array($curl, array(
//                     CURLOPT_URL => $endpoint."/flowable-rest/service/history/historic-process-instances?superProcessInstanceId=".$template->process_id."&includeProcessVariables=true&size=1000",
//                     CURLOPT_RETURNTRANSFER => true,
//                     CURLOPT_ENCODING => "",
//                     CURLOPT_TIMEOUT => 30000,
//                     CURLOPT_USERPWD => "rest-admin:test",
//                     CURLOPT_HTTP_VERSION => CURL_HTTP_VERSION_1_1,
//                     CURLOPT_CUSTOMREQUEST => "GET",
//                     CURLOPT_HTTPHEADER => array(
//                         // Set Here Your Requesred Headers
//                         'Content-Type: application/json',
//                     ),
//                 ));
//                 $response = curl_exec($curl);
//                 $err = curl_error($curl);
//                 curl_close($curl);
                
//                 $response = json_decode($response);
//                 foreach ($response->data as $process)
//                 {
//                     foreach ($process->variables as $variable)
//                     {
//                         if (strcmp($variable->name,"body") == 0)
//                         {
//                             $response_node = json_decode($variable->value);
//                             if(strcmp($node->name,$response_node->name) == 0)
//                             {
//                                 foreach ($process->variables as $variable)
//                                 {
//                                     if(strcmp($variable->name,"checkDeploymentUnitStatus") == 0)
//                                     {
//                                         $hasEnded = TRUE;
//                                         if (strcmp($variable->value,"ok") == 0)
//                                             $template_info = $template_info.$node->name.": COMPLETED\n";
//                                         else 
//                                         {
//                                             $template_info = "DEPLOYMENT STATUS: FAILED\n";
//                                             $template_info = $template_info.$node->name.": FAILED\n";
//                                         }
//                                         break;
//                                     }
//                                 }
//                                 break;
//                             }
//                         }
//                     }
//                 }
//             } 
//         }
//     }
// }
